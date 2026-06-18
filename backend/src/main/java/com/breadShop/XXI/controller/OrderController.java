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
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.OrderService;

/**
 * [Claude] OrderController — จัดการ order ของ user
 * path: /api/orders/**
 *
 * userId ทุกตัวดึงจาก JWT เท่านั้น ห้าม trust จาก request param/body (IDOR fix)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    public OrderController(OrderService orderService, UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    /**
     * GET /api/orders
     * ดึง order ทั้งหมดของ user ที่ login อยู่
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal String email) {
        User user = resolveUser(email);
        return ResponseEntity.ok(ApiResponse.ok(orderService.getByUserId(user.getId())));
    }

    /**
     * GET /api/orders/{id}
     * ดึง order ตาม id — ใช้ใน order detail page
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getById(id)));
    }

    /**
     * POST /api/orders
     * สร้าง order ใหม่ — userId override จาก JWT เสมอ (ป้องกัน IDOR)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @AuthenticationPrincipal String email,
            @RequestBody OrderRequest request) {
        User user = resolveUser(email);
        request.setUserId(user.getId()); // override userId จาก JWT เสมอ
        return ResponseEntity.ok(ApiResponse.ok("สร้าง order สำเร็จ", orderService.createOrder(request)));
    }

    /**
     * PATCH /api/orders/{id}/cancel
     * ยกเลิก order — ได้เฉพาะ status PENDING และต้องเป็น order ของ user คนนั้น
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable Integer id,
            @AuthenticationPrincipal String email) {
        User user = resolveUser(email);
        return ResponseEntity.ok(ApiResponse.ok("ยกเลิก order สำเร็จ",
                orderService.cancelOrder(id, user.getId())));
    }
}
