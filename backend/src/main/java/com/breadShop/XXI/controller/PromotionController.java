package com.breadShop.XXI.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ActiveRequest;
import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.promotion.PromotionRequest;
import com.breadShop.XXI.dto.promotion.PromotionResponse;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;

import jakarta.validation.Valid;
import com.breadShop.XXI.service.PromotionService;

/**
 * /api/v1/promotions — โค้ดส่วนลด
 * ลูกค้าเรียกได้แค่ GET /discount ที่เหลือ admin เท่านั้น (กำหนดใน SecurityConfig)
 */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("สร้างโปรโมชั่นสำเร็จ", promotionService.create(request)));
    }

    /** ?code=SALE50&amount=500 — ส่วนลดที่จะได้จากโค้ดนี้ (ตอน checkout) โค้ดใช้ไม่ได้ → 400 */
    @GetMapping("/discount")
    public ResponseEntity<ApiResponse<PromotionValidateResponse>> validate(
            @RequestParam String code, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.validate(code, amount)));
    }

    /** เปิด/ปิดโค้ดชั่วคราว { "active": false } */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> setActive(
            @PathVariable Integer id, @Valid @RequestBody ActiveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("อัปเดตสถานะโปรโมชั่นสำเร็จ",
                promotionService.setActive(id, request.active())));
    }

    /** order ที่เคยใช้โค้ดนี้ยังอยู่ (orders.promotion_id ถูกตั้งเป็น NULL) */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        promotionService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("ลบโปรโมชั่นสำเร็จ", null));
    }
}
