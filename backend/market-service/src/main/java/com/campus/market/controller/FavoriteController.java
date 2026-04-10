package com.campus.market.controller;

import com.campus.common.api.ApiResponse;
import com.campus.market.dto.FavoriteToggleRequest;
import com.campus.market.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/market/favorites")
public class FavoriteController {

    private final ProductService productService;

    public FavoriteController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/toggle")
    public ApiResponse<Map<String, Object>> toggle(@RequestBody FavoriteToggleRequest request) {
        return ApiResponse.success(productService.toggleFavorite(request));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> myFavorites(@RequestParam Long userId,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(productService.pageFavoriteProducts(userId, page, size));
    }
}

