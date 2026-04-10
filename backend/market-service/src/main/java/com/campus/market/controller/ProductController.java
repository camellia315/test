package com.campus.market.controller;

import com.campus.common.api.ApiResponse;
import com.campus.market.dto.ProductCategoryCreateRequest;
import com.campus.market.dto.ProductStatusUpdateRequest;
import com.campus.market.dto.ProductUpdateRequest;
import com.campus.market.dto.ProductUpsertRequest;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductCategory;
import com.campus.market.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "12") int size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) String sortBy,
                                                     @RequestParam(required = false) String sortOrder,
                                                     @RequestParam(required = false) Long viewerUserId,
                                                     @RequestParam(required = false) Long sellerUserId) {
        return ApiResponse.success(productService.pageProducts(
                page, size, keyword, categoryId, status, sortBy, sortOrder, viewerUserId, sellerUserId
        ));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Product> detail(@PathVariable Long id,
                                       @RequestParam(required = false) Long viewerUserId) {
        return ApiResponse.success(productService.detail(id, viewerUserId));
    }

    @PostMapping("/products")
    public ApiResponse<Product> create(@RequestBody ProductUpsertRequest request) {
        return ApiResponse.success(productService.create(request));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<Product> update(@PathVariable Long id, @RequestBody ProductUpdateRequest request) {
        return ApiResponse.success(productService.update(id, request));
    }

    @PutMapping("/products/{id}/status")
    public ApiResponse<Product> updateStatus(@PathVariable Long id, @RequestBody ProductStatusUpdateRequest request) {
        return ApiResponse.success(productService.updateStatus(id, request));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id, @RequestParam Long operatorUserId) {
        productService.delete(id, operatorUserId);
        return ApiResponse.success(Map.of("deleted", true));
    }

    @GetMapping("/categories")
    public ApiResponse<List<ProductCategory>> categories() {
        return ApiResponse.success(productService.listCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<ProductCategory> createCategory(@RequestBody ProductCategoryCreateRequest request) {
        return ApiResponse.success(productService.createCategory(request));
    }
}

