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

    /** ลูกค้ากรอกโค้ดตอน checkout — ผ่านทุกเงื่อนไขแล้วบอกว่าจะได้ลดเท่าไร */
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

    /** FIXED = ลดตามจำนวน, PERCENT = ลด % ของยอด (ไม่เกิน maxDiscount ถ้ากำหนดไว้) */
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal orderAmount) {
        if (promotion.getDiscountType() == DiscountType.FIXED) {
            return promotion.getDiscountValue();
        }
        BigDecimal discount = orderAmount.multiply(promotion.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal max = promotion.getMaxDiscount();
        return max != null && discount.compareTo(max) > 0 ? max : discount;
    }

    @Transactional
    public void incrementUsedCount(Integer promotionId) {
        Promotion promotion = findPromotion(promotionId);
        promotion.setUsedCount(promotion.getUsedCount() + 1);
        promotionRepository.save(promotion);
    }

    @Transactional
    public PromotionResponse setActive(Integer id, boolean active) {
        Promotion promotion = findPromotion(id);
        promotion.setIsActive(active);
        return toResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public void delete(Integer id) {
        if (!promotionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found");
        }
        promotionRepository.deleteById(id);
    }

    // ─── helpers ───────────────────────────────────────────────────────

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
