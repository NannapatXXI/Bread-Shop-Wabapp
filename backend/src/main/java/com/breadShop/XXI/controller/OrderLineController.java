package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.service.OrderLineService;

/**
 * [Claude] OrderLineController — รายการสินค้าในแต่ละ order
 * path: /api/orders/{orderId}/lines/**
 *
 * OrderLine = 1 row ใน order เช่น "ขนมปัง 2 ชิ้น ราคา 100 บาท"
 * order 1 ใบมีหลาย line ได้
 */
@RestController
@RequestMapping("/api/orders/{orderId}/lines")
public class OrderLineController {

    private final OrderLineService orderLineService;
    private final UserRepository userRepository;

    public OrderLineController(OrderLineService orderLineService, UserRepository userRepository) {
        this.orderLineService = orderLineService;
        this.userRepository = userRepository;
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    /**
     * GET /api/orders/1/lines
     * ดึง line ทั้งหมดของ order นั้น
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderLineResponse>>> getAll(
            @PathVariable Integer orderId,
            @AuthenticationPrincipal String email) {
        User user = resolveUser(email);
        return ResponseEntity.ok(ApiResponse.ok(orderLineService.getByOrderId(orderId, user.getId())));
    }

    /**
     * GET /api/orders/1/lines/2
     * ดึง line เดียวตาม id
     */
    @GetMapping("/{lineId}")
    public ResponseEntity<ApiResponse<OrderLineResponse>> getOne(
            @PathVariable Integer orderId,
            @PathVariable Integer lineId,
            @AuthenticationPrincipal String email) {
        User user = resolveUser(email);
        return ResponseEntity.ok(ApiResponse.ok(orderLineService.getById(orderId, lineId, user.getId())));
    }
}
