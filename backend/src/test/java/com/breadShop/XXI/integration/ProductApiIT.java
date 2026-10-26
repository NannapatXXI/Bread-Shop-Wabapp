package com.breadShop.XXI.integration;

import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test: Product API
 *
 * ทดสอบ endpoint จริง → เรียก MySQL จริง → ตรวจ response จริง
 *
 * @Transactional = ทุก test method รัน transaction แยก และ rollback หลังจบ
 *                  ทำให้ test data ไม่เลอะ DB จริงแม้แต่ record เดียว
 *
 * @EnabledIfSystemProperty = ป้องกันรันโดยไม่ตั้งใจในเครื่อง dev ที่ไม่มี MySQL
 *                            เปิดด้วย: mvnw test -Dintegration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Product API Integration Tests")
class ProductApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // สินค้าทดสอบ — ถูกสร้างก่อนแต่ละ test และ rollback หลังจบ
        productRepository.save(new Product(
                "IT Test Croissant",
                new BigDecimal("45.00"),
                10,
                "สินค้าสำหรับ integration test",
                null,
                ProductCategory.BREAD,
                LocalDate.of(2027, 12, 31)
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/products
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /products → ควรคืนสินค้าที่เพิ่งบันทึกลง DB")
    void getProducts_shouldReturnProductFromDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                // ตรวจ wrapper format: { success: true, data: [...] }
                .andExpect(jsonPath("$.success").value(true))
                // ตรวจว่า product ที่ insert จริงๆ อยู่ใน response
                .andExpect(jsonPath("$.data[*].name", hasItem("IT Test Croissant")));
    }

    @Test
    @DisplayName("GET /products → response ต้องมี price และ stock ที่ถูกต้อง")
    void getProducts_shouldReturnCorrectPriceAndStock() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'IT Test Croissant')].price")
                        .value(hasItem(45.0)))
                .andExpect(jsonPath("$.data[?(@.name == 'IT Test Croissant')].stock")
                        .value(hasItem(10)));
    }

    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/products/count?category=
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /products/count?category=BREAD → ต้องนับ product จาก DB จริง")
    void countByCategory_shouldCountFromRealDatabase() throws Exception {
        // @BeforeEach insert BREAD ไปแล้ว 1 ชิ้น ค่านี้จึงรวมสินค้าทดสอบไว้แล้ว
        long expected = productRepository.countByCategory(ProductCategory.BREAD);

        mockMvc.perform(get("/api/v1/products/count")
                        .param("category", "BREAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(expected));
    }

    @Test
    @DisplayName("GET /products/count?category=INVALID → ควรคืน 4xx")
    void countByCategory_withInvalidCategory_shouldReturn4xx() throws Exception {
        mockMvc.perform(get("/api/v1/products/count")
                        .param("category", "PIZZA"))
                .andExpect(status().is4xxClientError());
    }
}
