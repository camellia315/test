package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.entity.Product;
import com.campus.market.entity.UserBrowseHistory;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.UserBrowseHistoryMapper;
import com.campus.market.service.RecommendService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl implements RecommendService {

    private static final int STATUS_ON_SALE = 1;

    private final ProductMapper productMapper;
    private final UserBrowseHistoryMapper browseHistoryMapper;
    private final MarketCacheService marketCacheService;

    public RecommendServiceImpl(ProductMapper productMapper,
                                UserBrowseHistoryMapper browseHistoryMapper,
                                MarketCacheService marketCacheService) {
        this.productMapper = productMapper;
        this.browseHistoryMapper = browseHistoryMapper;
        this.marketCacheService = marketCacheService;
    }

    @Override
    public List<Product> recommendForUser(Long userId, int size) {
        int targetSize = normalizeSize(size);
        if (userId == null || userId <= 0) {
            return hotProducts(targetSize);
        }
        String cacheKey = "market:recommend:" + userId + ":" + targetSize;
        List<Product> cached = marketCacheService.getProducts(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<UserBrowseHistory> histories = browseHistoryMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getBrowseTime)
                        .last("LIMIT 120")
        );
        if (histories.isEmpty()) {
            return hotProducts(targetSize);
        }

        List<Long> recentProductIds = histories.stream()
                .map(UserBrowseHistory::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (recentProductIds.isEmpty()) {
            return hotProducts(targetSize);
        }

        List<Product> historyProducts = productMapper.selectBatchIds(recentProductIds);
        if (historyProducts.isEmpty()) {
            return hotProducts(targetSize);
        }

        Map<Long, Integer> categoryWeight = new HashMap<>();
        Map<String, Integer> tagWeight = new HashMap<>();
        for (Product product : historyProducts) {
            if (product.getCategoryId() != null) {
                categoryWeight.merge(product.getCategoryId(), 1, Integer::sum);
            }
            for (String tag : splitTags(product.getTags())) {
                tagWeight.merge(tag, 1, Integer::sum);
            }
        }

        Set<Long> viewedProductIds = new HashSet<>(recentProductIds);
        List<Product> candidates = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, STATUS_ON_SALE)
                        .orderByDesc(Product::getFavoriteCount)
                        .orderByDesc(Product::getViewCount)
                        .orderByDesc(Product::getCreateTime)
                        .last("LIMIT 300")
        );

        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        for (Product candidate : candidates) {
            if (viewedProductIds.contains(candidate.getId())) {
                continue;
            }
            double score = 0;
            if (candidate.getCategoryId() != null) {
                score += categoryWeight.getOrDefault(candidate.getCategoryId(), 0) * 4.0;
            }
            int tagScore = 0;
            for (String tag : splitTags(candidate.getTags())) {
                tagScore += tagWeight.getOrDefault(tag, 0);
            }
            score += tagScore * 2.0;
            score += safeNumber(candidate.getFavoriteCount()) * 0.4;
            score += safeNumber(candidate.getViewCount()) * 0.1;
            score += cheapBonus(candidate.getPrice());
            scoreMap.put(candidate.getId(), score);
        }

        List<Product> sorted = candidates.stream()
                .filter(item -> scoreMap.containsKey(item.getId()))
                .sorted(Comparator.comparingDouble((Product p) -> scoreMap.getOrDefault(p.getId(), 0D)).reversed())
                .limit(targetSize)
                .collect(Collectors.toCollection(ArrayList::new));

        if (sorted.size() < targetSize) {
            List<Product> hot = hotProducts(targetSize);
            Set<Long> existing = sorted.stream().map(Product::getId).collect(Collectors.toSet());
            for (Product hotProduct : hot) {
                if (existing.contains(hotProduct.getId())) {
                    continue;
                }
                sorted.add(hotProduct);
                if (sorted.size() >= targetSize) {
                    break;
                }
            }
        }

        marketCacheService.putProducts(cacheKey, sorted, Duration.ofMinutes(10));
        return sorted;
    }

    @Override
    public List<Product> hotProducts(int size) {
        int targetSize = normalizeSize(size);
        String cacheKey = "market:hot:" + targetSize;
        List<Product> cached = marketCacheService.getProducts(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<Product> rows = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, STATUS_ON_SALE)
                        .orderByDesc(Product::getFavoriteCount)
                        .orderByDesc(Product::getViewCount)
                        .orderByDesc(Product::getCreateTime)
                        .last("LIMIT " + targetSize)
        );
        marketCacheService.putProducts(cacheKey, rows, Duration.ofMinutes(5));
        return rows;
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        String[] parts = tags.split("[,，]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String tag = part == null ? "" : part.trim().toLowerCase();
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        return result;
    }

    private int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private double cheapBonus(BigDecimal price) {
        if (price == null) {
            return 0;
        }
        if (price.doubleValue() <= 50) {
            return 1.5;
        }
        if (price.doubleValue() <= 200) {
            return 0.8;
        }
        return 0;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 50);
    }
}

