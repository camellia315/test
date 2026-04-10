package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.dto.OrderStatusUpdateRequest;
import com.campus.market.entity.MarketOrder;
import com.campus.market.entity.Product;
import com.campus.market.mapper.MarketOrderMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.OrderService;
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

    private static final int PRODUCT_STATUS_ON_SALE = 1;
    private static final int PRODUCT_STATUS_SOLD = 2;

    private final MarketOrderMapper marketOrderMapper;
    private final ProductMapper productMapper;
    private final MarketCacheService marketCacheService;
    private final Random random = new Random();

    public OrderServiceImpl(MarketOrderMapper marketOrderMapper,
                            ProductMapper productMapper,
                            MarketCacheService marketCacheService) {
        this.marketOrderMapper = marketOrderMapper;
        this.productMapper = productMapper;
        this.marketCacheService = marketCacheService;
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
        if (marketOrderMapper.countActiveOrdersByProduct(request.getProductId()) > 0) {
            throw new BusinessException(409, "product already has active order");
        }

        MarketOrder order = new MarketOrder();
        order.setOrderNo(generateOrderNo());
        order.setProductId(request.getProductId());
        order.setBuyerId(request.getBuyerId());
        order.setSellerId(product.getSellerId());
        order.setPrice(product.getPrice());
        order.setStatus(STATUS_PENDING);
        order.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        marketOrderMapper.insert(order);

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
        validateTransition(current, target, operatorIsSeller, operatorIsBuyer);

        order.setStatus(target);
        if (StringUtils.hasText(request.getRemark())) {
            order.setRemark(request.getRemark().trim());
        }
        order.setUpdateTime(LocalDateTime.now());
        marketOrderMapper.updateById(order);

        if (target == STATUS_CONFIRMED || target == STATUS_COMPLETED) {
            product.setStatus(PRODUCT_STATUS_SOLD);
            productMapper.updateById(product);
        } else if (target == STATUS_CANCELED) {
            product.setStatus(PRODUCT_STATUS_ON_SALE);
            productMapper.updateById(product);
        }

        marketCacheService.evictByPrefix("market:hot:");
        marketCacheService.evictByPrefix("market:recommend:");
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

    private String generateOrderNo() {
        return "MO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + (100 + random.nextInt(900));
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
}

