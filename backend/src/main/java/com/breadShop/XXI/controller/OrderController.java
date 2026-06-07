package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.service.OrderService;

/**
 * [Claude] OrderController — จัดการ order ของ user
 * path: /api/orders/**
 *
 * หมายเหตุ: admin ใช้ PATCH status ผ่าน AdminController (/api/v1/admin/{id}/status)
 * controller นี้เป็นของฝั่ง user (ดู/สร้าง order)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /api/orders?userId=1
     * ดึง order ทั้งหมดของ user คนนั้น — เรียงตาม createdAt ล่าสุดก่อน (ทำใน service)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByUser(@RequestParam Integer userId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getByUserId(userId)));
    }

    /**
     * GET /api/orders/1
     * ดึง order ตาม id — ใช้ใน order detail page
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getById(id)));
    }

    /**
     * POST /api/orders
     * สร้าง order ใหม่ — ลด stock สินค้าอัตโนมัติ (ดูใน OrderService.createOrder)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("สร้าง order สำเร็จ", orderService.createOrder(request)));
    }

    /**
     * PATCH /api/orders/{id}/cancel?userId=1
     * ยกเลิก order — ได้เฉพาะ status PENDING และต้องเป็น order ของ user คนนั้นเท่านั้น
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable Integer id,
            @RequestParam Integer userId) {
        return ResponseEntity.ok(ApiResponse.ok("ยกเลิก order สำเร็จ", orderService.cancelOrder(id, userId)));
    }
}
