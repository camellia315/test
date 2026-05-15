package com.campus.market.service;

import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.dto.OrderPaySimulateRequest;
import com.campus.market.dto.OrderStatusUpdateRequest;
import com.campus.market.entity.MarketOrder;

import java.util.Map;

public interface OrderService {
    MarketOrder createOrder(OrderCreateRequest request);

    MarketOrder updateOrderStatus(Long orderId, OrderStatusUpdateRequest request);

    MarketOrder simulatePay(Long orderId, OrderPaySimulateRequest request);

    Map<String, Object> pageOrders(Long userId, String role, Integer status, int page, int size);
}
