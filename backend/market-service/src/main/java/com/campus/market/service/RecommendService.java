package com.campus.market.service;

import com.campus.market.entity.Product;

import java.util.List;

public interface RecommendService {
    List<Product> recommendForUser(Long userId, int size);

    List<Product> hotProducts(int size);
}

