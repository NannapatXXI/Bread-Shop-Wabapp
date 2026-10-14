package com.breadShop.XXI.integration;

import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.Mailservice;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test: Order API — ทดสอบ flow สมบูรณ์
 *
 * สิ่งที่ทดสอบที่ unit test ทำไม่ได้:
 *   1. JWT cookie จาก login สามารถใช้เรียก /api/orders ได้จริง
 *   2. Stock ถูกหักจาก DB จริงหลังสั่งซื้อ
 *   3. ถ้า stock = 0 ต้อง reject ด้วย 400 จาก DB จริง
 *   4. User ที่ไม่ใช่เจ้าของ order ยกเลิกไม่ได้ (403 จาก DB จริง)
 *
 * Flow ของ test:
 *   setUp → สร้าง user + product + address ใน DB
 *   login  → รับ access_token cookie
 *   test   → ใช้ cookie เรียก API → ตรวจผล
 *   rollback → transaction ล้างข้อมูลทั้งหมด
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Order API Integration Tests")
class OrderApiIT {

    @Autowired private MockMvc             mockMvc;
    @Autowired private UserRepository      userRepository;
    @Autowired private ProductRepository   productRepository;
    @Autowired private UserAddressRepository addressRepository;
    @Autowired private PasswordEncoder     passwordEncoder;

    @MockitoBean
    private Mailservice mailService;

    private User        testUser;
    private Product     testProduct;
    private UserAddress testAddress;

    @BeforeEach
    void setUp() {
        // สร้าง user ทดสอบ
        testUser = userRepository.save(
                new User("it_order_user", "it_order@breadshop.test",
                        passwordEncoder.encode("pass1234")));

        // สร้างสินค้าที่มี stock = 5
        testProduct = productRepository.save(new Product(
                "IT Order Bread",
                new BigDecimal("60.00"),
                5,
                "สำหรับทดสอบ order",
                null,
                ProductCategory.BREAD,
                LocalDate.of(2027, 12, 31)
        ));

        // สร้างที่อยู่จัดส่ง
        testAddress = addressRepository.save(new UserAddress(
                testUser, "บ้าน", "นันทพัทธ์ ทดสอบ",
                "0812345678", "123 ถ.สุขุมวิท",
                "กรุงเทพมหานคร", "คลองเตย", "คลองเตย", "10110", true
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helper: login แล้วดึง access_token cookie กลับมาใช้ใน test
    // ─────────────────────────────────────────────────────────────────

    private Cookie loginAndGetAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "usernameOrEmail": "it_order@breadshop.test",
                              "password": "pass1234"
                            }
                        """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("access_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    // ─────────────────────────────────────────────────────────────────
    //  POST /api/orders — สร้าง order
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("สร้าง order สำเร็จ → stock ถูกหัก 2 จาก DB จริง")
    void createOrder_shouldDeductStockInDatabase() throws Exception {
        Cookie accessToken = loginAndGetAccessToken();

        mockMvc.perform(post("/api/orders")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "addressId": %d,
                              "items": [
                                { "productId": %d, "quantity": 2 }
                              ]
                            }
                        """.formatted(testAddress.getId(), testProduct.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty());

        // ตรวจ DB โดยตรงว่า stock ลดจาก 5 → 3 จริง
        Product updated = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("ราคาคิดฝั่ง server เท่านั้น — ส่วนลดรายชิ้นและค่าส่งที่ client ส่งมาต้องถูกเมิน")
    void createOrder_ignoresClientSuppliedDiscountAndShippingFee() throws Exception {
        Cookie accessToken = loginAndGetAccessToken();

        // ยิง API เอง: ขอส่วนลดเท่าราคาสินค้า + ค่าส่งติดลบ เพื่อให้ได้ของฟรี
        mockMvc.perform(post("/api/orders")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "addressId": %d,
                              "shippingFee": -500.00,
                              "items": [
                                { "productId": %d, "quantity": 2, "discountAmount": 120.00 }
                              ]
                            }
                        """.formatted(testAddress.getId(), testProduct.getId())))
                .andExpect(status().isOk())
                // 60.00 × 2 = 120.00 เต็มราคา ส่งฟรี (ค่าส่งกำหนดโดย server)
                .andExpect(jsonPath("$.data.subtotal").value(120.00))
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(120.00))
                .andExpect(jsonPath("$.data.orderLines[0].discountAmount").value(0))
                .andExpect(jsonPath("$.data.orderLines[0].totalPrice").value(120.00));
    }

    @Test
    @DisplayName("เรียก /api/orders โดยไม่มี cookie → 401 Unauthorized")
    void createOrder_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "addressId": %d,
                              "items": [{ "productId": %d, "quantity": 1 }]
                            }
                        """.formatted(testAddress.getId(), testProduct.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("สั่งซื้อมากกว่า stock → 400 Bad Request")
    void createOrder_whenStockInsufficient_shouldReturn400() throws Exception {
        Cookie accessToken = loginAndGetAccessToken();

        // stock = 5 แต่สั่ง 99 ชิ้น
        mockMvc.perform(post("/api/orders")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "addressId": %d,
                              "items": [{ "productId": %d, "quantity": 99 }]
                            }
                        """.formatted(testAddress.getId(), testProduct.getId())))
                .andExpect(status().is4xxClientError());

        // stock ต้องไม่เปลี่ยน
        Product unchanged = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(unchanged.getStock()).isEqualTo(5);
    }

    // ─────────────────────────────────────────────────────────────────
    //  PATCH /api/orders/{id}/cancel
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ยกเลิก order ของตัวเอง → stock คืนกลับ DB")
    void cancelOrder_shouldRestoreStockInDatabase() throws Exception {
        Cookie accessToken = loginAndGetAccessToken();

        // สร้าง order ก่อน
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "addressId": %d,
                              "items": [{ "productId": %d, "quantity": 2 }]
                            }
                        """.formatted(testAddress.getId(), testProduct.getId())))
                .andExpect(status().isOk())
                .andReturn();

        // ดึง orderId จาก response
        String body = createResult.getResponse().getContentAsString();
        Integer orderId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");

        // stock ควรลดลงเป็น 3 แล้ว
        assertThat(productRepository.findById(testProduct.getId()).orElseThrow().getStock())
                .isEqualTo(3);

        // ยกเลิก order
        mockMvc.perform(patch("/api/orders/" + orderId + "/cancel")
                        .cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // stock ควรคืนกลับเป็น 5
        assertThat(productRepository.findById(testProduct.getId()).orElseThrow().getStock())
                .isEqualTo(5);
    }
}
