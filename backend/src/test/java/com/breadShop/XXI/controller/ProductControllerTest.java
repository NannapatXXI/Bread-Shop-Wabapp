package com.breadShop.XXI.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.service.ProductService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(ProductController.class)
class ProductControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ProductService productService;

    private Product product(long id, String name, String price, int stock, ProductCategory category) {
        Product p = new Product(name, new BigDecimal(price), stock, "desc " + name,
                "uploads/" + id + ".jpg", category, LocalDate.of(2027, 1, 31));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void getAllProducts_isPublic_andMapsEveryField() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(
                product(1, "Donut", "20.00", 5, ProductCategory.BREAD),
                product(2, "Chiffon", "80.00", 0, ProductCategory.CAKE)));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Donut"))
                .andExpect(jsonPath("$.data[0].price").value(20.00))
                .andExpect(jsonPath("$.data[0].stock").value(5))
                .andExpect(jsonPath("$.data[0].imageUrl").value("uploads/1.jpg"))
                .andExpect(jsonPath("$.data[0].category").value("BREAD"))
                .andExpect(jsonPath("$.data[0].expiryDate").value("2027-01-31"))
                .andExpect(jsonPath("$.data[1].stock").value(0));
    }

    @Test
    void getAllProducts_stillPublic_whenBrowserSendsGarbageToken() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products").cookie(new Cookie("access_token", "not-a-jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void countByCategory_acceptsLowercase_andPassesEnumToService() throws Exception {
        when(productService.countProductCategory(ProductCategory.CAKE)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/products/count").param("category", "cake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3))
                .andExpect(jsonPath("$.message").value("จำนวนสินค้าประเภท cake"));

        verify(productService).countProductCategory(ProductCategory.CAKE);
    }

    @Test
    void countByCategory_unknownCategory_returns400_withoutCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/products/count").param("category", "PIZZA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ไม่พบหมวดหมู่สินค้า: PIZZA"))
                .andExpect(content().string(not(containsString("com.breadShop"))));

        verify(productService, never()).countProductCategory(any());
    }

    @Test
    void count_withNoFilter_orBothFilters_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/count"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/products/count").param("category", "CAKE").param("stockAbove", "1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void countByStock_passesThresholdToService() throws Exception {
        when(productService.countProductsByStock(10)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/products/count").param("stockAbove", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7))
                .andExpect(jsonPath("$.message").value("จำนวนสินค้าที่มี stock > 10"));
    }

    @Test
    void countByStock_nonNumericParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/count").param("stockAbove", "ten"))
                .andExpect(status().isBadRequest());
    }
}
