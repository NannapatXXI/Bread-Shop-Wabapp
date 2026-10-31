package com.breadShop.XXI.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.dto.storefront.AdminStorefrontResponse;
import com.breadShop.XXI.dto.storefront.StorefrontResponse;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.StorefrontService;

@WebMvcTest({ StorefrontController.class, AdminStorefrontController.class })
class StorefrontControllerTest extends ControllerTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";

    @MockitoBean private StorefrontService storefrontService;
    @MockitoBean private AuditLogService auditLogService;

    private static ProductResponse donut() {
        return new ProductResponse(2L, "donut", new BigDecimal("20.00"), 5, "เคลือบน้ำตาล", "uploads/d.jpg",
                ProductCategory.BREAD, LocalDate.of(2027, 1, 1), true);
    }

    // ───────────── สาธารณะ ─────────────

    @Test
    void storefront_isPublic_noLoginNeeded() throws Exception {
        when(storefrontService.getStorefront()).thenReturn(new StorefrontResponse("uploads/cover.jpg", List.of(donut())));

        mockMvc.perform(get("/api/v1/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heroImageUrl").value("uploads/cover.jpg"))
                .andExpect(jsonPath("$.data.featured[0].name").value("donut"));
    }

    @Test
    void storefront_withExpiredToken_stillWorks() throws Exception {
        when(storefrontService.getStorefront()).thenReturn(new StorefrontResponse(null, List.of()));

        mockMvc.perform(get("/api/v1/storefront").cookie(expiredToken(ALICE)))
                .andExpect(status().isOk());
    }

    // ───────────── admin เท่านั้น ─────────────

    @Test
    void adminEndpoints_withoutToken_401_asUser_403() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/admin/storefront")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/storefront/featured").with(json("{\"productIds\":[1]}")).cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/storefront/hero-image").cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(storefrontService, auditLogService);
    }

    @Test
    void adminView_returnsConfiguredPicks() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(storefrontService.getForAdmin()).thenReturn(new AdminStorefrontResponse(null, List.of(3L, 1L)));

        mockMvc.perform(get("/api/v1/admin/storefront").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featuredProductIds[0]").value(3))
                .andExpect(jsonPath("$.data.featuredProductIds[1]").value(1));
    }

    @Test
    void setFeatured_passesIdsInOrder_andAudits() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(put("/api/v1/admin/storefront/featured").with(json("{\"productIds\":[3,1,7]}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("บันทึกสินค้าแนะนำสำเร็จ"));

        verify(storefrontService).setFeatured(List.of(3L, 1L, 7L));
        verify(auditLogService).log(eq(admin), eq("UPDATE_FEATURED"), eq("Storefront"), eq("home"),
                isNull(), eq("[3, 1, 7]"), anyString());
    }

    @Test
    void setFeatured_moreThanEight_or_missingList_returns400() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(put("/api/v1/admin/storefront/featured").with(json("{\"productIds\":[1,2,3,4,5,6,7,8,9]}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("เลือกสินค้าแนะนำได้สูงสุด 8 รายการ"));
        mockMvc.perform(put("/api/v1/admin/storefront/featured").with(json("{}")).cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storefrontService);
    }

    @Test
    void uploadHeroImage_isMultipart_andReturnsTheNewPath() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(storefrontService.setHeroImage(any())).thenReturn("uploads/new.jpg");
        MockMultipartFile image = new MockMultipartFile("image", "cover.jpg", "image/jpeg", new byte[] { 1, 2 });

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/admin/storefront/hero-image").file(image)
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heroImageUrl").value("uploads/new.jpg"));
    }

    @Test
    void uploadHeroImage_wrongFileType_returns400WithThaiMessage() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(storefrontService.setHeroImage(any())).thenThrow(new IllegalArgumentException("INVALID_IMAGE_TYPE"));
        MockMultipartFile pdf = new MockMultipartFile("image", "menu.pdf", "application/pdf", new byte[] { 1 });

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/admin/storefront/hero-image").file(pdf)
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("รองรับเฉพาะไฟล์รูป jpg, png, webp, gif"));
    }

    @Test
    void removeHeroImage_callsService() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(delete("/api/v1/admin/storefront/hero-image").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(storefrontService).removeHeroImage();
    }
}
