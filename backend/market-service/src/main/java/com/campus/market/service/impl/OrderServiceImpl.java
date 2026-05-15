package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.dto.OrderPaySimulateRequest;
import com.campus.market.dto.OrderStatusUpdateRequest;
import com.campus.market.entity.MarketOrder;
import com.campus.market.entity.Product;
import com.campus.market.mapper.MarketOrderMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.OrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CONFIRMED = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_CANCELED = 3;
    private static final int PAY_UNPAID = 0;
    private static final int PAY_PAID = 1;
    private static final int TYPE_TRADE_NOTIFICATION = 2;

    private static final int PRODUCT_STATUS_OFFLINE = 0;
    private static final int PRODUCT_STATUS_ON_SALE = 1;
    private static final int PRODUCT_STATUS_SOLD = 2;

    private final MarketOrderMapper marketOrderMapper;
    private final ProductMapper productMapper;
    private final MarketCacheService marketCacheService;
    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random();

    public OrderServiceImpl(MarketOrderMapper marketOrderMapper,
                            ProductMapper productMapper,
                            MarketCacheService marketCacheService,
                            JdbcTemplate jdbcTemplate) {
        this.marketOrderMapper = marketOrderMapper;
        this.productMapper = productMapper;
        this.marketCacheService = marketCacheService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public MarketOrder createOrder(OrderCreateRequest request) {
        if (request == null || request.getProductId() == null || request.getBuyerId() == null) {
            throw new BusinessException(400, "productId and buyerId are required");
        }
        Product product = getProductOrThrow(request.getProductId());
        if (!Objects.equals(product.getStatus(), PRODUCT_STATUS_ON_SALE)) {
            throw new BusinessException(409, "product is not on sale");
        }
        if (Objects.equals(product.getSellerId(), request.getBuyerId())) {
            throw new BusinessException(409, "buyer cannot be product seller");
        }
        int totalQuantity = safeTotalQuantity(product);
        int soldQuantity = safeSoldQuantity(product);
        int pendingUnpaid = marketOrderMapper.countPendingUnpaidOrdersByProduct(request.getProductId());
        int availableQuantity = totalQuantity - soldQuantity - pendingUnpaid;
        if (availableQuantity <= 0) {
            product.setStatus(PRODUCT_STATUS_SOLD);
            productMapper.updateById(product);
            marketCacheService.evictByPrefix("market:hot:");
            marketCacheService.evictByPrefix("market:recommend:");
            throw new BusinessException(409, "product sold out");
        }

        MarketOrder order = new MarketOrder();
        order.setOrderNo(generateOrderNo());
        order.setProductId(request.getProductId());
        order.setBuyerId(request.getBuyerId());
        order.setSellerId(product.getSellerId());
        order.setPrice(product.getPrice());
        order.setStatus(STATUS_PENDING);
        order.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "");
        order.setPayStatus(PAY_UNPAID);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        marketOrderMapper.insert(order);

        pushTradeNotification(
                order.getSellerId(),
                "【二手市场-新订单】" + order.getOrderNo(),
                "用户#" + order.getBuyerId() + " 下单了你的商品《" + safeText(product.getTitle()) + "》，等待模拟支付。",
                "/market"
        );

        marketCacheService.evictByPrefix("market:hot:");
        marketCacheService.evictByPrefix("market:recommend:");
        return enrichOrders(List.of(order)).get(0);
    }

    @Override
    @Transactional
    public MarketOrder updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        if (request == null || request.getOperatorUserId() == null || request.getStatus() == null) {
            throw new BusinessException(400, "operatorUserId and status are required");
        }
        MarketOrder order = getOrderOrThrow(orderId);
        Product product = getProductOrThrow(order.getProductId());

        Long operator = request.getOperatorUserId();
        boolean operatorIsSeller = Objects.equals(operator, order.getSellerId());
        boolean operatorIsBuyer = Objects.equals(operator, order.getBuyerId());
        if (!operatorIsSeller && !operatorIsBuyer) {
            throw new BusinessException(403, "only buyer or seller can operate order");
        }

        Integer current = order.getStatus();
        Integer target = request.getStatus();
        if (target != null && target == STATUS_CONFIRMED && (order.getPayStatus() == null || order.getPayStatus() != PAY_PAID)) {
            throw new BusinessException(409, "order not paid yet, cannot confirm");
        }
        validateTransition(current, target, operatorIsSeller, operatorIsBuyer);

        order.setStatus(target);
        if (StringUtils.hasText(request.getRemark())) {
            order.setRemark(request.getRemark().trim());
        }
        order.setUpdateTime(LocalDateTime.now());
        marketOrderMapper.updateById(order);

        if (target == STATUS_CONFIRMED) {
            pushTradeNotification(
                    order.getBuyerId(),
                    "【二手市场-订单已确认】" + order.getOrderNo(),
                    "卖家已确认你的订单，商品《" + safeText(product.getTitle()) + "》正在等待完成。",
                    "/market"
            );
        } else if (target == STATUS_COMPLETED) {
            pushTradeNotification(
                    order.getSellerId(),
                    "【二手市场-订单已完成】" + order.getOrderNo(),
                    "买家已确认完成订单，商品《" + safeText(product.getTitle()) + "》交易结束。",
                    "/market"
            );
        } else if (target == STATUS_CANCELED) {
            rollbackSoldIfNeeded(order, product);
            Long notifyUser = operatorIsBuyer ? order.getSellerId() : order.getBuyerId();
            pushTradeNotification(
                    notifyUser,
                    "【二手市场-订单已取消】" + order.getOrderNo(),
                    "订单已被用户#" + operator + " 取消，商品《" + safeText(product.getTitle()) + "》已恢复上架状态。",
                    "/market"
            );
        }

        marketCacheService.evictByPrefix("market:hot:");
        marketCacheService.evictByPrefix("market:recommend:");
        return enrichOrders(List.of(getOrderOrThrow(orderId))).get(0);
    }

    @Override
    @Transactional
    public MarketOrder simulatePay(Long orderId, OrderPaySimulateRequest request) {
        if (request == null || request.getOperatorUserId() == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        MarketOrder order = getOrderOrThrow(orderId);
        if (!Objects.equals(order.getBuyerId(), request.getOperatorUserId())) {
            throw new BusinessException(403, "only buyer can simulate payment");
        }
        if (order.getStatus() == null || order.getStatus() != STATUS_PENDING) {
            throw new BusinessException(409, "only pending order can be paid");
        }
        if (order.getPayStatus() != null && order.getPayStatus() == PAY_PAID) {
            return enrichOrders(List.of(order)).get(0);
        }

        String channel = StringUtils.hasText(request.getPayChannel()) ? request.getPayChannel().trim() : "SIMULATED";
        order.setPayStatus(PAY_PAID);
        order.setPayChannel(channel);
        order.setPayOrderNo(generatePayOrderNo());
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        marketOrderMapper.updateById(order);

        Product product = getProductOrThrow(order.getProductId());
        int soldQuantity = safeSoldQuantity(product);
        int totalQuantity = safeTotalQuantity(product);
        if (soldQuantity >= totalQuantity) {
            product.setStatus(PRODUCT_STATUS_SOLD);
            productMapper.updateById(product);
            marketCacheService.evictByPrefix("market:hot:");
            marketCacheService.evictByPrefix("market:recommend:");
            throw new BusinessException(409, "product sold out");
        }
        productMapper.increaseSoldQuantity(product.getId());
        Product latestProduct = getProductOrThrow(product.getId());
        if (safeSoldQuantity(latestProduct) >= safeTotalQuantity(latestProduct)) {
            latestProduct.setStatus(PRODUCT_STATUS_SOLD);
        } else if (Objects.equals(latestProduct.getStatus(), PRODUCT_STATUS_SOLD)) {
            latestProduct.setStatus(PRODUCT_STATUS_ON_SALE);
        }
        productMapper.updateById(latestProduct);
        marketCacheService.evictByPrefix("market:hot:");
        marketCacheService.evictByPrefix("market:recommend:");
        pushTradeNotification(
                order.getSellerId(),
                "【二手市场-已模拟支付】" + order.getOrderNo(),
                "买家#" + order.getBuyerId() + " 已完成模拟支付，商品《" + safeText(latestProduct.getTitle()) + "》待你确认订单。",
                "/market"
        );
        pushTradeNotification(
                order.getBuyerId(),
                "【二手市场-支付成功】" + order.getOrderNo(),
                "你已完成模拟支付（渠道：" + channel + "），请等待卖家确认。",
                "/market"
        );
        return enrichOrders(List.of(getOrderOrThrow(orderId))).get(0);
    }

    @Override
    public Map<String, Object> pageOrders(Long userId, String role, Integer status, int page, int size) {
        if (userId == null) {
            throw new BusinessException(400, "userId is required");
        }
        Page<MarketOrder> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<MarketOrder> wrapper = new LambdaQueryWrapper<>();
        if ("seller".equalsIgnoreCase(role)) {
            wrapper.eq(MarketOrder::getSellerId, userId);
        } else if ("all".equalsIgnoreCase(role)) {
            wrapper.and(w -> w.eq(MarketOrder::getBuyerId, userId).or().eq(MarketOrder::getSellerId, userId));
        } else {
            wrapper.eq(MarketOrder::getBuyerId, userId);
        }
        if (status != null) {
            wrapper.eq(MarketOrder::getStatus, status);
        }
        wrapper.orderByDesc(MarketOrder::getCreateTime);
        Page<MarketOrder> result = marketOrderMapper.selectPage(pageReq, wrapper);
        result.setRecords(enrichOrders(result.getRecords()));
        return toPageData(result);
    }

    private List<MarketOrder> enrichOrders(List<MarketOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return orders == null ? List.of() : orders;
        }
        Set<Long> productIds = orders.stream().map(MarketOrder::getProductId).collect(java.util.stream.Collectors.toSet());
        List<Product> products = productIds.isEmpty() ? List.of() : productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }
        for (MarketOrder order : orders) {
            Product product = productMap.get(order.getProductId());
            if (product != null) {
                order.setProductTitle(product.getTitle());
                order.setProductCoverImage(product.getCoverImage());
            }
        }
        return orders;
    }

    private Product getProductOrThrow(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "product not found");
        }
        return product;
    }

    private MarketOrder getOrderOrThrow(Long orderId) {
        MarketOrder order = marketOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "order not found");
        }
        return order;
    }

    private void validateTransition(Integer current, Integer target, boolean operatorIsSeller, boolean operatorIsBuyer) {
        if (target == null || target < STATUS_PENDING || target > STATUS_CANCELED) {
            throw new BusinessException(400, "status must be 0/1/2/3");
        }
        if (Objects.equals(current, target)) {
            return;
        }
        if (current == STATUS_PENDING && target == STATUS_CONFIRMED && operatorIsSeller) {
            return;
        }
        if (current == STATUS_PENDING && target == STATUS_CANCELED) {
            return;
        }
        if (current == STATUS_CONFIRMED && target == STATUS_COMPLETED && operatorIsBuyer) {
            return;
        }
        if (current == STATUS_CONFIRMED && target == STATUS_CANCELED) {
            return;
        }
        throw new BusinessException(409, "invalid order status transition");
    }

    private int safeTotalQuantity(Product product) {
        if (product == null || product.getTotalQuantity() == null || product.getTotalQuantity() <= 0) {
            return 1;
        }
        return product.getTotalQuantity();
    }

    private int safeSoldQuantity(Product product) {
        if (product == null || product.getSoldQuantity() == null || product.getSoldQuantity() < 0) {
            return 0;
        }
        return product.getSoldQuantity();
    }

    private void rollbackSoldIfNeeded(MarketOrder order, Product product) {
        if (order == null || product == null) {
            return;
        }
        if (!Objects.equals(order.getPayStatus(), PAY_PAID)) {
            int totalQuantity = safeTotalQuantity(product);
            int soldQuantity = safeSoldQuantity(product);
            if (soldQuantity >= totalQuantity && !Objects.equals(product.getStatus(), PRODUCT_STATUS_SOLD)) {
                product.setStatus(PRODUCT_STATUS_SOLD);
                productMapper.updateById(product);
            } else if (soldQuantity < totalQuantity
                    && Objects.equals(product.getStatus(), PRODUCT_STATUS_SOLD)
                    && !Objects.equals(product.getStatus(), PRODUCT_STATUS_OFFLINE)) {
                product.setStatus(PRODUCT_STATUS_ON_SALE);
                productMapper.updateById(product);
            }
            return;
        }
        if (safeSoldQuantity(product) > 0) {
            productMapper.decreaseSoldQuantity(product.getId());
        }
        Product latest = getProductOrThrow(product.getId());
        if (safeSoldQuantity(latest) >= safeTotalQuantity(latest)) {
            latest.setStatus(PRODUCT_STATUS_SOLD);
        } else if (!Objects.equals(latest.getStatus(), PRODUCT_STATUS_OFFLINE)) {
            latest.setStatus(PRODUCT_STATUS_ON_SALE);
        }
        productMapper.updateById(latest);
    }

    private String generateOrderNo() {
        return "MO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + (100 + random.nextInt(900));
    }

    private String generatePayOrderNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + (1000 + random.nextInt(9000));
    }

    private int normalizePage(int page) {
        return page <= 0 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private Map<String, Object> toPageData(Page<?> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    private void pushTradeNotification(Long userId, String title, String content, String linkUrl) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO message_notification(user_id, type, title, content, link_url, is_read, create_time) VALUES (?, ?, ?, ?, ?, 0, NOW())",
                    userId,
                    TYPE_TRADE_NOTIFICATION,
                    title,
                    content,
                    linkUrl
            );
        } catch (Exception ignored) {
        }
    }

    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= 100) {
            return text;
        }
        return text.substring(0, 99) + "…";
    }
}
