package com.breadShop.XXI.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.breadShop.XXI.dto.promotion.PromotionResponse;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;
import com.breadShop.XXI.entity.Promotion.DiscountType;
import com.breadShop.XXI.service.PromotionService;

@WebMvcTest(PromotionController.class)
class PromotionControllerTest extends ControllerTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";

    @MockitoBean
    private PromotionService promotionService;

    private PromotionResponse promo(int id, boolean active) {
        return new PromotionResponse(id, "BREAD50", "ลด 50", DiscountType.FIXED, new BigDecimal("50"),
                new BigDecimal("200"), null, 10, 2, LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59), active);
    }

    // ───────────── discount: ลูกค้าที่ login แล้วใช้ได้ ─────────────

    @Test
    void discount_asLoggedInUser_passesCodeAndExactAmount() throws Exception {
        stubUser(7, ALICE, "USER");
        when(promotionService.validate("BREAD50", new BigDecimal("240.50")))
                .thenReturn(new PromotionValidateResponse("BREAD50", "ลด 50", new BigDecimal("50")));

        mockMvc.perform(get("/api/v1/promotions/discount").param("code", "BREAD50").param("amount", "240.50")
                        .cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount").value(50));

        verify(promotionService).validate("BREAD50", new BigDecimal("240.50"));
    }

    @Test
    void discount_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/discount").param("code", "BREAD50").param("amount", "100"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(promotionService);
    }

    @Test
    void discount_withNonNumericAmount_returns400() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/promotions/discount").param("code", "BREAD50").param("amount", "free")
                        .cookie(userToken(ALICE)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promotionService);
    }

    // ───────────── admin only ─────────────

    @Test
    void list_asAdmin_returnsPromotions() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(promotionService.getAll()).thenReturn(List.of(promo(1, true)));

        mockMvc.perform(get("/api/v1/promotions").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("BREAD50"))
                .andExpect(jsonPath("$.data[0].usedCount").value(2));
    }

    @Test
    void list_create_setActive_delete_asNormalUser_allReturn403() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/promotions").cookie(userToken(ALICE))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/promotions").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/promotions/1").with(json("{\"active\":false}")).cookie(userToken(ALICE))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/promotions/1").cookie(userToken(ALICE))).andExpect(status().isForbidden());

        verifyNoInteractions(promotionService);
    }

    @Test
    void create_asAdmin_returnsCreatedPromotion() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(promotionService.create(any())).thenReturn(promo(9, true));

        mockMvc.perform(post("/api/v1/promotions").cookie(adminToken(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "code": "BREAD50", "name": "ลด 50", "discountType": "FIXED", "discountValue": 50,
                              "startedAt": "2026-01-01T00:00:00", "expiredAt": "2026-12-31T23:59:00" }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("สร้างโปรโมชั่นสำเร็จ"))
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    void create_withBlankCodeAndNoDates_isRejectedByValidation() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(post("/api/v1/promotions").cookie(adminToken(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"code\": \"\", \"name\": \"x\", \"discountType\": \"FIXED\", \"discountValue\": 10 }"))
                .andExpect(status().isBadRequest());

        verify(promotionService, never()).create(any());
    }

    @Test
    void setActive_and_delete_asAdmin_callServiceWithId() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(promotionService.setActive(3, false)).thenReturn(promo(3, false));

        mockMvc.perform(patch("/api/v1/promotions/3").with(json("{\"active\":false}")).cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
        mockMvc.perform(delete("/api/v1/promotions/3").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ลบโปรโมชั่นสำเร็จ"));

        verify(promotionService).setActive(3, false);
        verify(promotionService).delete(3);
    }
}
