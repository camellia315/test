package com.campus.market.service;

import com.campus.market.dto.FavoriteToggleRequest;
import com.campus.market.dto.ProductCategoryCreateRequest;
import com.campus.market.dto.ProductStatusUpdateRequest;
import com.campus.market.dto.ProductUpdateRequest;
import com.campus.market.dto.ProductUpsertRequest;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductCategory;

import java.util.List;
import java.util.Map;

public interface ProductService {
    Map<String, Object> pageProducts(int page,
                                     int size,
                                     String keyword,
                                     Long categoryId,
                                     Integer status,
                                     String sortBy,
                                     String sortOrder,
                                     Long viewerUserId,
                                     Long sellerUserId);

    Product detail(Long productId, Long viewerUserId);

    Product create(ProductUpsertRequest request);

    Product update(Long productId, ProductUpdateRequest request);

    Product updateStatus(Long productId, ProductStatusUpdateRequest request);

    void delete(Long productId, Long operatorUserId);

    Map<String, Object> toggleFavorite(FavoriteToggleRequest request);

    Map<String, Object> pageFavoriteProducts(Long userId, int page, int size);

    List<ProductCategory> listCategories();

    ProductCategory createCategory(ProductCategoryCreateRequest request);
}

