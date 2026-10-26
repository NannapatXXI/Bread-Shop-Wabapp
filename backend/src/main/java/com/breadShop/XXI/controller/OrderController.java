package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.service.OrderService;

import jakarta.validation.Valid;

/** /api/v1/orders — order ของลูกค้าที่ login อยู่เท่านั้น (user มาจาก token เสมอ ไม่เชื่อ userId จาก body) */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUser  currentUser;

    public OrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser  = currentUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getByUserId(currentUser.get(email).getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(
            @PathVariable Integer id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getByIdForUser(id, currentUser.get(email).getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @AuthenticationPrincipal String email, @Valid @RequestBody OrderRequest request) {
        request.setUserId(currentUser.get(email).getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("สร้าง order สำเร็จ", orderService.createOrder(request)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable Integer id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok("ยกเลิก order สำเร็จ",
                orderService.cancelOrder(id, currentUser.get(email).getId())));
    }
}
