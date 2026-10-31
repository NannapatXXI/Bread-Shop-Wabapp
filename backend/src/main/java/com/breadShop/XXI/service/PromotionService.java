package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.promotion.PromotionRequest;
import com.breadShop.XXI.dto.promotion.PromotionResponse;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;
import com.breadShop.XXI.entity.Promotion;
import com.breadShop.XXI.entity.Promotion.DiscountType;
import com.breadShop.XXI.repository.PromotionRepository;

/** โค้ดส่วนลด: admin สร้าง/เปิดปิด/ลบ, ลูกค้าตรวจโค้ดก่อนสั่งซื้อ */
@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    public List<PromotionResponse> getAll() {
        return promotionRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * สร้าง promotion ใหม่ — ถ้า code ซ้ำจะ throw 400
     * @param request รับข้อมูลจาก admin ที่จะเอามาสร้าง promotion
     * @return PromotionResponse ของ promotion ที่สร้างแล้ว
     */
    public PromotionResponse create(PromotionRequest request) {
        if (promotionRepository.findByCode(request.getCode()).isPresent()) {
            throw badRequest("Promotion code already exists");
        }

        Promotion promotion = new Promotion(request.getCode(), request.getName(), request.getDiscountType(),
                request.getDiscountValue(), request.getStartedAt(), request.getExpiredAt());
        promotion.setMinOrderAmount(request.getMinOrderAmount());
        promotion.setMaxDiscount(request.getMaxDiscount());
        promotion.setUsageLimit(request.getUsageLimit());
        return toResponse(promotionRepository.save(promotion));
    }

    /**
     * ตรวจสอบโค้ดส่วนลด — ถ้าไม่ถูกต้อง/หมดอายุ/ปิดใช้งาน/ยอดสั่งซื้อต่ำกว่า minOrderAmount > throw 400 
     * @param code รหัสโค้ดส่วนลดที่ลูกค้าใส่
     * @param orderAmount ยอดสั่งซื้อของลูกค้า (ใช้ตรวจสอบ minOrderAmount และคำนวณส่วนลด)
     * @return PromotionValidateResponse ที่มี code, name, discountAmount (ส่วนลดที่ได้จากโค้ดนี้)
     */
    public PromotionValidateResponse validate(String code, BigDecimal orderAmount) {
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> badRequest("โค้ดส่วนลดไม่ถูกต้อง"));

        if (!promotion.getIsActive()) {
            throw badRequest("โค้ดนี้ถูกปิดใช้งานแล้ว");
        }
        if (promotion.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw badRequest("โค้ดนี้หมดอายุแล้ว");
        }
        if (promotion.getMinOrderAmount() != null && orderAmount.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw badRequest("Order amount is below minimum: " + promotion.getMinOrderAmount());
        }
        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw badRequest("Promotion code has reached usage limit");
        }

        return new PromotionValidateResponse(promotion.getCode(), promotion.getName(),
                calculateDiscount(promotion, orderAmount));
    }

    /**
     * คำนวณส่วนลดจาก promotion และยอดสั่งซื้อ — ถ้าเป็น FIXED จะคืนค่า discountValue, ถ้าเป็น PERCENT จะคำนวณจาก orderAmount และตรวจสอบ maxDiscount
     * @param promotion  object ของ Promotion ที่ตรวจสอบแล้วว่า valid
     * @param orderAmount ยอดสั่งซื้อของลูกค้า
     * @return BigDecimal ของส่วนลดที่ได้จาก promotion
     */
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal orderAmount) {
        if (promotion.getDiscountType() == DiscountType.FIXED) {
            return promotion.getDiscountValue();
        }
        BigDecimal discount = orderAmount.multiply(promotion.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal max = promotion.getMaxDiscount();
        return max != null && discount.compareTo(max) > 0 ? max : discount;
    }

    /**
     * เพิ่ม usedCount ของ promotion — ใช้ตอนลูกค้าสั่งซื้อสำเร็จ
     * @param promotionId id ของ Promotion
     */
    @Transactional
    public void incrementUsedCount(Integer promotionId) {
        Promotion promotion = findPromotion(promotionId);
        promotion.setUsedCount(promotion.getUsedCount() + 1);
        promotionRepository.save(promotion);
    }

    /**
     * เปิด/ปิดใช้งาน promotion — ใช้ตอน admin ต้องการปิดโค้ดชั่วคราว
     * @param id id ของ Promotion
     * @param active true = เปิดใช้งาน, false = ปิดใช้งาน
     * @return PromotionResponse ของ promotion ที่อัปเดตแล้ว
     */
    @Transactional
    public PromotionResponse setActive(Integer id, boolean active) {
        Promotion promotion = findPromotion(id);
        promotion.setIsActive(active);
        return toResponse(promotionRepository.save(promotion));
    }

    /**
     * ลบ promotion — ใช้ตอน admin ต้องการลบโค้ดออกจากระบบ
     * @param id id ของ Promotion
     */
    @Transactional
    public void delete(Integer id) {
        if (!promotionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found");
        }
        promotionRepository.deleteById(id);
    }

    // ─── helpers ───────────────────────────────────────────────────────
    /**
     * ค้นหา promotion ตาม id — ถ้าไม่เจอจะ throw 404
     * @param id id ของ Promotion
     * @return Promotion object
     */
    private Promotion findPromotion(Integer id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));
    }

    
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private PromotionResponse toResponse(Promotion p) {
        return new PromotionResponse(
                p.getId(), p.getCode(), p.getName(), p.getDiscountType(), p.getDiscountValue(),
                p.getMinOrderAmount(), p.getMaxDiscount(), p.getUsageLimit(), p.getUsedCount(),
                p.getStartedAt(), p.getExpiredAt(), p.getIsActive());
    }
}
