package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.service.OrderLineService;

/** /api/v1/orders/{orderId}/lines — รายการสินค้าใน order ของตัวเอง */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/lines")
public class OrderLineController {

    private final OrderLineService orderLineService;
    private final CurrentUser      currentUser;

    public OrderLineController(OrderLineService orderLineService, CurrentUser currentUser) {
        this.orderLineService = orderLineService;
        this.currentUser      = currentUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderLineResponse>>> getAll(
            @PathVariable Integer orderId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(orderLineService.getByOrderId(orderId, currentUser.get(email).getId())));
    }

    @GetMapping("/{lineId}")
    public ResponseEntity<ApiResponse<OrderLineResponse>> getOne(
            @PathVariable Integer orderId, @PathVariable Integer lineId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderLineService.getById(orderId, lineId, currentUser.get(email).getId())));
    }
}
