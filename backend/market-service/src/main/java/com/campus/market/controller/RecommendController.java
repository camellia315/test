package com.campus.market.controller;

import com.campus.common.api.ApiResponse;
import com.campus.market.entity.Product;
import com.campus.market.service.RecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping("/for-you")
    public ApiResponse<List<Product>> forYou(@RequestParam Long userId,
                                             @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(recommendService.recommendForUser(userId, size));
    }

    @GetMapping("/hot")
    public ApiResponse<List<Product>> hot(@RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(recommendService.hotProducts(size));
    }
}

