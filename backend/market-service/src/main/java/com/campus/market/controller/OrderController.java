package com.campus.market.controller;

import com.campus.common.api.ApiResponse;
import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.dto.OrderPaySimulateRequest;
import com.campus.market.dto.OrderStatusUpdateRequest;
import com.campus.market.entity.MarketOrder;
import com.campus.market.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/market/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<MarketOrder> create(@RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.createOrder(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<MarketOrder> updateStatus(@PathVariable Long id, @RequestBody OrderStatusUpdateRequest request) {
        return ApiResponse.success(orderService.updateOrderStatus(id, request));
    }

    @PostMapping("/{id}/pay/simulate")
    public ApiResponse<MarketOrder> simulatePay(@PathVariable Long id, @RequestBody OrderPaySimulateRequest request) {
        return ApiResponse.success(orderService.simulatePay(id, request));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> orders(@RequestParam Long userId,
                                                   @RequestParam(defaultValue = "all") String role,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(orderService.pageOrders(userId, role, status, page, size));
    }
}
