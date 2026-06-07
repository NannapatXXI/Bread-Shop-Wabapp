package com.breadShop.XXI.controller;

import java.math.BigDecimal;
import java.util.List;

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

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.promotion.PromotionRequest;
import com.breadShop.XXI.dto.promotion.PromotionResponse;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;
import com.breadShop.XXI.service.PromotionService;

/**
 * [Claude] PromotionController — จัดการโปรโมชั่น/คูปองส่วนลด
 * path: /api/promotions/**
 *
 * DiscountType มี 2 แบบ:
 *   FIXED   — ลดคงที่ เช่น ลด 50 บาท
 *   PERCENT — ลดเป็น % เช่น ลด 10% (ถ้ามี maxDiscount จะไม่เกินค่านั้น)
 */
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /**
     * GET /api/promotions
     * ดึงโปรโมชั่นทั้งหมด
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.getAll()));
    }

    /**
     * POST /api/promotions
     * สร้างโปรโมชั่นใหม่ — ควร ROLE_ADMIN เท่านั้น (TODO: เพิ่ม security)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("สร้างโปรโมชั่นสำเร็จ", promotionService.create(request)));
    }

    /**
     * GET /api/promotions/validate?code=SALE50&amount=500
     * ตรวจสอบว่า code ใช้ได้ไหม และคำนวณส่วนลดตามยอดที่ส่งมา
     * ใช้ตอน checkout ก่อน user กด confirm order
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<PromotionValidateResponse>> validate(
            @RequestParam String code,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.validate(code, amount)));
    }

    /**
     * PATCH /api/promotions/{id}/toggle
     * เปิด/ปิดโปรโมชั่น (toggle isActive)
     * Admin ใช้ตอนต้องการหยุดโปรโมชั่นชั่วคราว
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<PromotionResponse>> toggle(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("อัปเดตสถานะโปรโมชั่นสำเร็จ",
                promotionService.toggle(id)));
    }

    /**
     * DELETE /api/promotions/{id}
     * ลบโปรโมชั่น — FK orders.promotion_id จะถูก SET NULL อัตโนมัติ
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        promotionService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("ลบโปรโมชั่นสำเร็จ", null));
    }
}
