package com.breadShop.XXI.service;

import com.breadShop.XXI.dto.promotion.PromotionRequest;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;
import com.breadShop.XXI.entity.Promotion;
import com.breadShop.XXI.entity.Promotion.DiscountType;
import com.breadShop.XXI.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PromotionService
 * ครอบคลุม: calculateDiscount, validate, create, toggle
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionService Tests")
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private PromotionService promotionService;

    // ─────────────────────────────────────────────────────────────────────────
    //  calculateDiscount
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateDiscount")
    class CalculateDiscountTests {

        @Test
        @DisplayName("FIXED — คืนค่าส่วนลดแบบตายตัว")
        void fixedDiscount_shouldReturnFixedValue() {
            Promotion promo = buildPromo(DiscountType.FIXED, new BigDecimal("50"), null);

            BigDecimal result = promotionService.calculateDiscount(promo, new BigDecimal("300"));

            assertThat(result).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("PERCENT — คิด % จากยอดสั่งซื้อ")
        void percentDiscount_shouldReturnPercentageOfOrder() {
            Promotion promo = buildPromo(DiscountType.PERCENT, new BigDecimal("10"), null);
            // 10% of 200 = 20
            BigDecimal result = promotionService.calculateDiscount(promo, new BigDecimal("200"));

            assertThat(result).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("PERCENT + maxDiscount — ส่วนลดต้องไม่เกิน maxDiscount")
        void percentDiscount_withMaxCap_shouldBeCapped() {
            Promotion promo = buildPromo(DiscountType.PERCENT, new BigDecimal("20"), new BigDecimal("100"));
            // 20% of 1000 = 200 แต่ cap ที่ 100
            BigDecimal result = promotionService.calculateDiscount(promo, new BigDecimal("1000"));

            assertThat(result).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("PERCENT + maxDiscount — ถ้าส่วนลดไม่เกิน cap ก็คิดตาม % ปกติ")
        void percentDiscount_belowCap_shouldReturnActualPercent() {
            Promotion promo = buildPromo(DiscountType.PERCENT, new BigDecimal("10"), new BigDecimal("100"));
            // 10% of 500 = 50 — ไม่เกิน cap 100
            BigDecimal result = promotionService.calculateDiscount(promo, new BigDecimal("500"));

            assertThat(result).isEqualByComparingTo("50.00");
        }

        private Promotion buildPromo(DiscountType type, BigDecimal value, BigDecimal maxDiscount) {
            Promotion p = new Promotion("CODE", "Test", type, value,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30));
            p.setMaxDiscount(maxDiscount);
            return p;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  validate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate")
    class ValidateTests {

        @Test
        @DisplayName("โค้ดถูกต้อง + ยอดผ่านขั้นต่ำ → คืน PromotionValidateResponse")
        void validate_withValidCode_shouldReturnResponse() {
            Promotion promo = new Promotion("BREAD10", "ลด 10%", DiscountType.PERCENT,
                    new BigDecimal("10"), LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(10));
            promo.setMinOrderAmount(new BigDecimal("100"));

            when(promotionRepository.findByCode("BREAD10")).thenReturn(Optional.of(promo));

            PromotionValidateResponse res = promotionService.validate("BREAD10", new BigDecimal("300"));

            assertThat(res.getCode()).isEqualTo("BREAD10");
            assertThat(res.getDiscountAmount()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("โค้ดไม่มีในระบบ → throw 400")
        void validate_withInvalidCode_shouldThrow400() {
            when(promotionRepository.findByCode("BADCODE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.validate("BADCODE", new BigDecimal("500")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("ไม่ถูกต้อง");
        }

        @Test
        @DisplayName("ยอดสั่งซื้อต่ำกว่า minOrderAmount → throw 400")
        void validate_belowMinOrder_shouldThrow400() {
            Promotion promo = new Promotion("BIG50", "ลด 50 บาท", DiscountType.FIXED,
                    new BigDecimal("50"), LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(10));
            promo.setMinOrderAmount(new BigDecimal("500"));

            when(promotionRepository.findByCode("BIG50")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> promotionService.validate("BIG50", new BigDecimal("200")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("minimum");
        }

        @Test
        @DisplayName("โค้ดถึง usage limit แล้ว → throw 400")
        void validate_whenUsageLimitReached_shouldThrow400() {
            Promotion promo = new Promotion("LIMITED", "จำกัด 5 ครั้ง", DiscountType.FIXED,
                    new BigDecimal("30"), LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(10));
            promo.setUsageLimit(5);
            promo.setUsedCount(5);  // เต็มแล้ว

            when(promotionRepository.findByCode("LIMITED")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> promotionService.validate("LIMITED", new BigDecimal("300")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("usage limit");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  create
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — โค้ดซ้ำ → throw 400")
    void create_withDuplicateCode_shouldThrow400() {
        Promotion existing = new Promotion("DUP", "เดิม", DiscountType.FIXED,
                new BigDecimal("10"), LocalDateTime.now(), LocalDateTime.now().plusDays(10));

        when(promotionRepository.findByCode("DUP")).thenReturn(Optional.of(existing));

        PromotionRequest request = mock(PromotionRequest.class);
        when(request.getCode()).thenReturn("DUP");

        assertThatThrownBy(() -> promotionService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  toggle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggle — โปรโมชั่น active=true → เปลี่ยนเป็น false")
    void toggle_whenActive_shouldDeactivate() {
        Promotion promo = new Promotion("TOGGLE", "ทดสอบ", DiscountType.FIXED,
                new BigDecimal("20"), LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        promo.setIsActive(true);

        when(promotionRepository.findById(1)).thenReturn(Optional.of(promo));
        when(promotionRepository.save(any())).thenReturn(promo);

        promotionService.toggle(1);

        assertThat(promo.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("toggle — โปรโมชั่น active=false → เปลี่ยนเป็น true")
    void toggle_whenInactive_shouldActivate() {
        Promotion promo = new Promotion("TOGGLE2", "ทดสอบ", DiscountType.FIXED,
                new BigDecimal("20"), LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        promo.setIsActive(false);

        when(promotionRepository.findById(2)).thenReturn(Optional.of(promo));
        when(promotionRepository.save(any())).thenReturn(promo);

        promotionService.toggle(2);

        assertThat(promo.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("toggle — ไม่พบ id → throw 404")
    void toggle_whenNotFound_shouldThrow404() {
        when(promotionRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.toggle(999))
                .isInstanceOf(ResponseStatusException.class);
    }
}
