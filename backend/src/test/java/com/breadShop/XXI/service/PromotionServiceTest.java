package com.breadShop.XXI.service;

import com.breadShop.XXI.dto.promotion.PromotionRequest;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;
import com.breadShop.XXI.entity.Promotion;
import com.breadShop.XXI.entity.Promotion.DiscountType;
import com.breadShop.XXI.repository.PromotionRepository;
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

    // ─────────────────────────────────────────────────────────────────────────
    //  getAll
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAll — คืนรายการโปรโมชั่นทั้งหมดแปลงเป็น response")
    void getAll_shouldReturnAllPromotions() {
        Promotion promo = new Promotion("ALL10", "ทดสอบ", DiscountType.FIXED,
                new BigDecimal("10"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
        when(promotionRepository.findAll()).thenReturn(java.util.List.of(promo));

        var result = promotionService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("ALL10");
    }

    @Test
    @DisplayName("getAll — ไม่มีโปรโมชั่นเลย → คืน list ว่าง")
    void getAll_whenEmpty_shouldReturnEmptyList() {
        when(promotionRepository.findAll()).thenReturn(java.util.List.of());
        assertThat(promotionService.getAll()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  create — success
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — โค้ดไม่ซ้ำ → สร้างสำเร็จและคืน PromotionResponse")
    void create_withNewCode_shouldSaveSuccessfully() {
        PromotionRequest request = mock(PromotionRequest.class);
        when(request.getCode()).thenReturn("NEW20");
        when(request.getName()).thenReturn("ลด 20 บาท");
        when(request.getDiscountType()).thenReturn(DiscountType.FIXED);
        when(request.getDiscountValue()).thenReturn(new BigDecimal("20"));
        when(request.getStartedAt()).thenReturn(LocalDateTime.now());
        when(request.getExpiredAt()).thenReturn(LocalDateTime.now().plusDays(30));
        when(request.getMinOrderAmount()).thenReturn(null);
        when(request.getMaxDiscount()).thenReturn(null);
        when(request.getUsageLimit()).thenReturn(null);

        when(promotionRepository.findByCode("NEW20")).thenReturn(Optional.empty());
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = promotionService.create(request);

        assertThat(result.getCode()).isEqualTo("NEW20");
        verify(promotionRepository).save(any(Promotion.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  validate — additional branches
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("validate — โปรโมชั่นถูกปิดใช้งาน → throw 400")
    void validate_whenInactive_shouldThrow400() {
        Promotion promo = new Promotion("OFF10", "ปิดใช้งาน", DiscountType.FIXED,
                new BigDecimal("10"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
        promo.setIsActive(false);

        when(promotionRepository.findByCode("OFF10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promotionService.validate("OFF10", new BigDecimal("100")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ปิดใช้งาน");
    }

    @Test
    @DisplayName("validate — โปรโมชั่นหมดอายุแล้ว → throw 400")
    void validate_whenExpired_shouldThrow400() {
        Promotion promo = new Promotion("EXP10", "หมดอายุ", DiscountType.FIXED,
                new BigDecimal("10"), LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(1));

        when(promotionRepository.findByCode("EXP10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promotionService.validate("EXP10", new BigDecimal("100")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("หมดอายุ");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  incrementUsedCount
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("incrementUsedCount — พบโปรโมชั่น → เพิ่ม usedCount ขึ้น 1")
    void incrementUsedCount_whenFound_shouldIncrement() {
        Promotion promo = new Promotion("INC1", "ทดสอบ", DiscountType.FIXED,
                new BigDecimal("10"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
        promo.setUsedCount(2);

        when(promotionRepository.findById(1)).thenReturn(Optional.of(promo));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        promotionService.incrementUsedCount(1);

        assertThat(promo.getUsedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("incrementUsedCount — ไม่พบโปรโมชั่น → throw 404")
    void incrementUsedCount_whenNotFound_shouldThrow404() {
        when(promotionRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.incrementUsedCount(999))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  delete
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — พบโปรโมชั่น → ลบสำเร็จ")
    void delete_whenFound_shouldDelete() {
        when(promotionRepository.existsById(1)).thenReturn(true);

        promotionService.delete(1);

        verify(promotionRepository).deleteById(1);
    }

    @Test
    @DisplayName("delete — ไม่พบโปรโมชั่น → throw 404")
    void delete_whenNotFound_shouldThrow404() {
        when(promotionRepository.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> promotionService.delete(999))
                .isInstanceOf(ResponseStatusException.class);

        verify(promotionRepository, never()).deleteById(any());
    }
}
