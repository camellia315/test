package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.market.dto.FavoriteToggleRequest;
import com.campus.market.dto.ProductCategoryCreateRequest;
import com.campus.market.dto.ProductStatusUpdateRequest;
import com.campus.market.dto.ProductUpdateRequest;
import com.campus.market.dto.ProductUpsertRequest;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductCategory;
import com.campus.market.entity.ProductFavorite;
import com.campus.market.entity.UserBrowseHistory;
import com.campus.market.mapper.MarketOrderMapper;
import com.campus.market.mapper.ProductCategoryMapper;
import com.campus.market.mapper.ProductFavoriteMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.UserBrowseHistoryMapper;
import com.campus.market.service.ProductService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int STATUS_OFFLINE = 0;
    private static final int STATUS_ON_SALE = 1;
    private static final int STATUS_SOLD = 2;
    private static final int DEFAULT_TOTAL_QUANTITY = 1;

    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final ProductFavoriteMapper productFavoriteMapper;
    private final UserBrowseHistoryMapper userBrowseHistoryMapper;
    private final MarketOrderMapper marketOrderMapper;
    private final MarketCacheService marketCacheService;

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductCategoryMapper productCategoryMapper,
                              ProductFavoriteMapper productFavoriteMapper,
                              UserBrowseHistoryMapper userBrowseHistoryMapper,
                              MarketOrderMapper marketOrderMapper,
                              MarketCacheService marketCacheService) {
        this.productMapper = productMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productFavoriteMapper = productFavoriteMapper;
        this.userBrowseHistoryMapper = userBrowseHistoryMapper;
        this.marketOrderMapper = marketOrderMapper;
        this.marketCacheService = marketCacheService;
    }

    @Override
    public Map<String, Object> pageProducts(int page,
                                            int size,
                                            String keyword,
                                            Long categoryId,
                                            Integer status,
                                            String sortBy,
                                            String sortOrder,
                                            Long viewerUserId,
                                            Long sellerUserId) {
        Page<Product> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Product::getTitle, kw).or().like(Product::getDescription, kw));
        }
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (sellerUserId != null) {
            wrapper.eq(Product::getSellerId, sellerUserId);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        } else if (sellerUserId == null) {
            wrapper.eq(Product::getStatus, STATUS_ON_SALE);
        }

        applySort(wrapper, sortBy, sortOrder);

        Page<Product> result = productMapper.selectPage(pageReq, wrapper);
        List<Product> records = enrichProducts(result.getRecords(), viewerUserId);
        result.setRecords(records);
        return toPageData(result);
    }

    @Override
    @Transactional
    public Product detail(Long productId, Long viewerUserId) {
        Product product = getProductOrThrow(productId);
        productMapper.increaseViewCount(productId);
        Product latest = getProductOrThrow(productId);
        if (viewerUserId != null && viewerUserId > 0 && !Objects.equals(viewerUserId, latest.getSellerId())) {
            UserBrowseHistory history = new UserBrowseHistory();
            history.setUserId(viewerUserId);
            history.setProductId(productId);
            history.setBrowseTime(LocalDateTime.now());
            userBrowseHistoryMapper.insert(history);
        }
        List<Product> enriched = enrichProducts(List.of(latest), viewerUserId);
        return enriched.isEmpty() ? latest : enriched.get(0);
    }

    @Override
    @Transactional
    public Product create(ProductUpsertRequest request) {
        validateUpsertRequest(request);
        Product product = new Product();
        mergeProduct(product, request);
        product.setStatus(STATUS_ON_SALE);
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setSoldQuantity(0);
        if (product.getTotalQuantity() == null || product.getTotalQuantity() <= 0) {
            product.setTotalQuantity(DEFAULT_TOTAL_QUANTITY);
        }
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);
        evictRecommendCache(request.getSellerId());
        return getProductOrThrow(product.getId());
    }

    @Override
    @Transactional
    public Product update(Long productId, ProductUpdateRequest request) {
        if (request == null || request.getOperatorUserId() == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        Product product = getProductOrThrow(productId);
        ensureSeller(product, request.getOperatorUserId());
        if (request.getSellerId() != null && !request.getSellerId().equals(product.getSellerId())) {
            throw new BusinessException(403, "sellerId cannot be changed");
        }
        mergeProduct(product, request);
        Integer soldQuantity = safeSoldQuantity(product);
        Integer totalQuantity = safeTotalQuantity(product);
        if (totalQuantity < soldQuantity) {
            throw new BusinessException(400, "totalQuantity cannot be less than soldQuantity");
        }
        if (soldQuantity >= totalQuantity) {
            product.setStatus(STATUS_SOLD);
        } else if (Objects.equals(product.getStatus(), STATUS_SOLD)) {
            product.setStatus(STATUS_ON_SALE);
        }
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        evictRecommendCache(product.getSellerId());
        return getProductOrThrow(productId);
    }

    @Override
    @Transactional
    public Product updateStatus(Long productId, ProductStatusUpdateRequest request) {
        if (request == null || request.getOperatorUserId() == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        if (request.getStatus() == null || request.getStatus() < STATUS_OFFLINE || request.getStatus() > STATUS_SOLD) {
            throw new BusinessException(400, "status must be 0/1/2");
        }
        Product product = getProductOrThrow(productId);
        ensureSeller(product, request.getOperatorUserId());
        if (request.getStatus() == STATUS_ON_SALE && safeSoldQuantity(product) >= safeTotalQuantity(product)) {
            throw new BusinessException(409, "stock is sold out, cannot set on sale");
        }
        product.setStatus(request.getStatus());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        evictRecommendCache(product.getSellerId());
        return getProductOrThrow(productId);
    }

    @Override
    @Transactional
    public void delete(Long productId, Long operatorUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        Product product = getProductOrThrow(productId);
        ensureSeller(product, operatorUserId);
        int activeOrders = marketOrderMapper.countActiveOrdersByProduct(productId);
        if (activeOrders > 0) {
            throw new BusinessException(409, "product has active orders and cannot be deleted");
        }
        productFavoriteMapper.delete(new LambdaQueryWrapper<ProductFavorite>().eq(ProductFavorite::getProductId, productId));
        productMapper.deleteById(productId);
        evictRecommendCache(product.getSellerId());
    }

    @Override
    @Transactional
    public Map<String, Object> toggleFavorite(FavoriteToggleRequest request) {
        if (request == null || request.getProductId() == null || request.getUserId() == null) {
            throw new BusinessException(400, "productId and userId are required");
        }
        Product product = getProductOrThrow(request.getProductId());
        if (Objects.equals(product.getSellerId(), request.getUserId())) {
            throw new BusinessException(409, "cannot favorite your own product");
        }
        ProductFavorite existed = productFavoriteMapper.selectByProductAndUser(request.getProductId(), request.getUserId());
        boolean favorited;
        if (existed != null) {
            productFavoriteMapper.deleteByProductAndUser(request.getProductId(), request.getUserId());
            productMapper.decreaseFavoriteCount(request.getProductId());
            favorited = false;
        } else {
            ProductFavorite favorite = new ProductFavorite();
            favorite.setProductId(request.getProductId());
            favorite.setUserId(request.getUserId());
            favorite.setCreateTime(LocalDateTime.now());
            try {
                productFavoriteMapper.insert(favorite);
                productMapper.increaseFavoriteCount(request.getProductId());
            } catch (DuplicateKeyException ex) {
                // Ignore duplicate inserts from concurrent clicks.
            }
            favorited = true;
        }
        Product latest = getProductOrThrow(request.getProductId());
        evictRecommendCache(request.getUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", favorited);
        result.put("favoriteCount", latest.getFavoriteCount() == null ? 0 : latest.getFavoriteCount());
        return result;
    }

    @Override
    public Map<String, Object> pageFavoriteProducts(Long userId, int page, int size) {
        if (userId == null) {
            throw new BusinessException(400, "userId is required");
        }
        Page<ProductFavorite> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<ProductFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductFavorite::getUserId, userId);
        wrapper.orderByDesc(ProductFavorite::getCreateTime);
        Page<ProductFavorite> favoritePage = productFavoriteMapper.selectPage(pageReq, wrapper);

        List<Long> productIds = favoritePage.getRecords().stream()
                .map(ProductFavorite::getProductId)
                .filter(Objects::nonNull)
                .toList();

        if (productIds.isEmpty()) {
            return toPageData(new Page<Product>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal()));
        }

        List<Product> products = productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : enrichProducts(products, userId)) {
            productMap.put(product.getId(), product);
        }

        List<Product> ordered = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product != null) {
                ordered.add(product);
            }
        }

        Page<Product> converted = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        converted.setRecords(ordered);
        return toPageData(converted);
    }

    @Override
    public List<ProductCategory> listCategories() {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProductCategory::getSort).orderByAsc(ProductCategory::getId);
        return productCategoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ProductCategory createCategory(ProductCategoryCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException(400, "category name is required");
        }
        ProductCategory category = new ProductCategory();
        category.setName(request.getName().trim());
        category.setIcon(StringUtils.hasText(request.getIcon()) ? request.getIcon().trim() : "");
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        productCategoryMapper.insert(category);
        return category;
    }

    private Product getProductOrThrow(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "product not found");
        }
        return product;
    }

    private void ensureSeller(Product product, Long operatorUserId) {
        if (!Objects.equals(product.getSellerId(), operatorUserId)) {
            throw new BusinessException(403, "only seller can operate this product");
        }
    }

    private void validateUpsertRequest(ProductUpsertRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(400, "title is required");
        }
        if (request.getPrice() == null || request.getPrice().doubleValue() <= 0) {
            throw new BusinessException(400, "price must be greater than 0");
        }
        if (request.getSellerId() == null) {
            throw new BusinessException(400, "sellerId is required");
        }
        if (request.getTotalQuantity() != null && request.getTotalQuantity() <= 0) {
            throw new BusinessException(400, "totalQuantity must be greater than 0");
        }
    }

    private void mergeProduct(Product target, ProductUpsertRequest request) {
        validateUpsertRequest(request);
        target.setTitle(request.getTitle().trim());
        target.setDescription(request.getDescription());
        target.setCoverImage(request.getCoverImage());
        target.setImages(request.getImages());
        target.setPrice(request.getPrice());
        target.setOriginalPrice(request.getOriginalPrice());
        target.setCategoryId(request.getCategoryId());
        target.setTags(request.getTags());
        if (request.getTotalQuantity() != null) {
            target.setTotalQuantity(normalizeTotalQuantity(request.getTotalQuantity()));
        } else if (target.getTotalQuantity() == null || target.getTotalQuantity() <= 0) {
            target.setTotalQuantity(DEFAULT_TOTAL_QUANTITY);
        }
        if (target.getSoldQuantity() == null || target.getSoldQuantity() < 0) {
            target.setSoldQuantity(0);
        }
        if (target.getSoldQuantity() > target.getTotalQuantity()) {
            target.setSoldQuantity(target.getTotalQuantity());
        }
        target.setSellerId(request.getSellerId());
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sortBy, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        String normalizedSort = sortBy == null ? "" : sortBy.trim();
        if ("price".equalsIgnoreCase(normalizedSort)) {
            wrapper.orderBy(true, asc, Product::getPrice);
            wrapper.orderByDesc(Product::getCreateTime);
            return;
        }
        if ("viewCount".equalsIgnoreCase(normalizedSort)) {
            wrapper.orderBy(true, asc, Product::getViewCount);
            wrapper.orderByDesc(Product::getCreateTime);
            return;
        }
        if ("favoriteCount".equalsIgnoreCase(normalizedSort)) {
            wrapper.orderBy(true, asc, Product::getFavoriteCount);
            wrapper.orderByDesc(Product::getCreateTime);
            return;
        }
        wrapper.orderByDesc(Product::getCreateTime);
    }

    private List<Product> enrichProducts(List<Product> products, Long viewerUserId) {
        if (products == null || products.isEmpty()) {
            return products == null ? List.of() : products;
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        for (Product product : products) {
            if (product.getCategoryId() != null) {
                categoryIds.add(product.getCategoryId());
            }
            if (product.getId() != null) {
                productIds.add(product.getId());
            }
        }

        Map<Long, String> categoryNameMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<ProductCategory> categories = productCategoryMapper.selectBatchIds(categoryIds);
            for (ProductCategory category : categories) {
                categoryNameMap.put(category.getId(), category.getName());
            }
        }

        Set<Long> favoritedProductIds = new HashSet<>();
        if (viewerUserId != null && viewerUserId > 0 && !productIds.isEmpty()) {
            LambdaQueryWrapper<ProductFavorite> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProductFavorite::getUserId, viewerUserId);
            wrapper.in(ProductFavorite::getProductId, productIds);
            List<ProductFavorite> favorites = productFavoriteMapper.selectList(wrapper);
            for (ProductFavorite favorite : favorites) {
                favoritedProductIds.add(favorite.getProductId());
            }
        }

        List<Product> enriched = new ArrayList<>(products.size());
        for (Product product : products) {
            Product copy = product;
            copy.setCategoryName(categoryNameMap.getOrDefault(copy.getCategoryId(), ""));
            copy.setFavorited(favoritedProductIds.contains(copy.getId()));
            enriched.add(copy);
        }
        return enriched;
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

    private int normalizeTotalQuantity(Integer totalQuantity) {
        if (totalQuantity == null || totalQuantity <= 0) {
            return DEFAULT_TOTAL_QUANTITY;
        }
        return Math.min(totalQuantity, 100000);
    }

    private int safeTotalQuantity(Product product) {
        if (product == null || product.getTotalQuantity() == null || product.getTotalQuantity() <= 0) {
            return DEFAULT_TOTAL_QUANTITY;
        }
        return product.getTotalQuantity();
    }

    private int safeSoldQuantity(Product product) {
        if (product == null || product.getSoldQuantity() == null || product.getSoldQuantity() < 0) {
            return 0;
        }
        return product.getSoldQuantity();
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

    private void evictRecommendCache(Long userId) {
        marketCacheService.evictByPrefix("market:recommend:");
        marketCacheService.evictByPrefix("market:hot:");
        if (userId != null) {
            marketCacheService.evictByPrefix("market:recommend:" + userId + ":");
        }
    }
}
