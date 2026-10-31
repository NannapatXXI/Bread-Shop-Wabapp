package com.breadShop.XXI.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.entity.Notification;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.NotificationRepository;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.Mailservice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/**
 * Broken Access Control (OWASP A01): ผู้ใช้คนหนึ่งต้องอ่าน/แก้ข้อมูลของอีกคนไม่ได้
 * ใช้ Spring เต็มระบบ + MySQL จริง (ต้องใส่ -Dintegration=true) และ rollback ทุกเทส
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Access control (IDOR) Integration Tests")
class AccessControlIT {

    // RateLimitInterceptor จำกัด login 10 ครั้ง/นาที/IP — แยก IP ต่อการ login แต่ละครั้ง
    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);
    private static final String PASSWORD = "pass1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserAddressRepository addressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private Mailservice mailService;

    private User alice;
    private User bob;
    private UserAddress aliceAddress;
    private UserAddress bobAddress;
    private Product product;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("it_acl_alice", "it_acl_alice@breadshop.test", passwordEncoder.encode(PASSWORD)));
        bob = userRepository.save(new User("it_acl_bob", "it_acl_bob@breadshop.test", passwordEncoder.encode(PASSWORD)));
        product = productRepository.save(new Product("IT ACL Bread", new BigDecimal("30.00"), 10,
                "สำหรับทดสอบสิทธิ์", null, ProductCategory.BREAD, LocalDate.of(2027, 12, 31)));
        aliceAddress = addressRepository.save(new UserAddress(alice, "บ้าน Alice", "Alice", "0811111111",
                "1 ถนน A", "กรุงเทพมหานคร", "ปทุมวัน", "ลุมพินี", "10330", true));
        bobAddress = addressRepository.save(new UserAddress(bob, "บ้าน Bob", "Bob", "0822222222",
                "2 ถนน B", "กรุงเทพมหานคร", "บางรัก", "สีลม", "10500", true));
    }

    private Cookie login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("172.16.0." + IP_SEQ.getAndIncrement());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usernameOrEmail\": \"" + user.getEmail() + "\", \"password\": \"" + PASSWORD + "\" }"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("access_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private JsonNode placeOrder(Cookie token, UserAddress address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders").cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"addressId\": " + address.getId()
                                + ", \"items\": [{ \"productId\": " + product.getId() + ", \"quantity\": 1 }] }"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static ResultMatcher deniedAccess() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("ต้องปฏิเสธด้วย 403 หรือ 404 — body: %s", result.getResponse().getContentAsString())
                .isIn(403, 404);
    }

    // ───────────── positive controls: เจ้าของต้องเข้าถึงของตัวเองได้ ─────────────

    @Test
    @DisplayName("เจ้าของ order ดู order และรายการสินค้าของตัวเองได้")
    void owner_canReadOwnOrderAndLines() throws Exception {
        Cookie aliceToken = login(alice);
        JsonNode order = placeOrder(aliceToken, aliceAddress);
        int orderId = order.get("id").asInt();
        int lineId = order.get("orderLines").get(0).get("id").asInt();

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(alice.getId()));
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/lines").cookie(aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/lines/" + lineId).cookie(aliceToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("เจ้าของแก้ที่อยู่ของตัวเองได้")
    void owner_canUpdateOwnAddress() throws Exception {
        Cookie aliceToken = login(alice);

        mockMvc.perform(put("/api/v1/users/me/addresses/" + aliceAddress.getId()).cookie(aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"คอนโด\", \"recipientName\": \"Alice\", \"phone\": \"0811111111\", \"isDefault\": true }"))
                .andExpect(status().isOk());

        assertThat(addressRepository.findById(aliceAddress.getId()).orElseThrow().getName()).isEqualTo("คอนโด");
    }

    // ───────────── orders ─────────────

    @Test
    @DisplayName("[A01] Bob ดู order ของ Alice ไม่ได้")
    void otherUser_cannotReadSomeoneElsesOrder() throws Exception {
        int aliceOrderId = placeOrder(login(alice), aliceAddress).get("id").asInt();

        mockMvc.perform(get("/api/v1/orders/" + aliceOrderId).cookie(login(bob)))
                .andExpect(deniedAccess());
    }

    @Test
    @DisplayName("[A01] Bob ดูรายการสินค้าใน order ของ Alice ไม่ได้")
    void otherUser_cannotReadSomeoneElsesOrderLines() throws Exception {
        JsonNode aliceOrder = placeOrder(login(alice), aliceAddress);
        int aliceOrderId = aliceOrder.get("id").asInt();
        int aliceLineId = aliceOrder.get("orderLines").get(0).get("id").asInt();
        Cookie bobToken = login(bob);

        mockMvc.perform(get("/api/v1/orders/" + aliceOrderId + "/lines").cookie(bobToken))
                .andExpect(deniedAccess());
        mockMvc.perform(get("/api/v1/orders/" + aliceOrderId + "/lines/" + aliceLineId).cookie(bobToken))
                .andExpect(deniedAccess());
    }

    @Test
    @DisplayName("[A01] ใส่ orderId ของตัวเองแต่ lineId ของคนอื่น ต้องไม่ได้ข้อมูล")
    void otherUser_cannotReadLineThroughOwnOrderPath() throws Exception {
        int aliceLineId = placeOrder(login(alice), aliceAddress).get("orderLines").get(0).get("id").asInt();
        Cookie bobToken = login(bob);
        // เลข order ซ้ำได้ถ้าสร้างในวินาทีเดียวกัน (ดู twoOrdersInTheSameSecond_bothSucceed) — รอให้ข้ามวินาทีก่อน
        Thread.sleep(1_100);
        int bobOrderId = placeOrder(bobToken, bobAddress).get("id").asInt();

        mockMvc.perform(get("/api/v1/orders/" + bobOrderId + "/lines/" + aliceLineId).cookie(bobToken))
                .andExpect(deniedAccess());
    }

    @Test
    @DisplayName("[A01] Bob ยกเลิก order ของ Alice ไม่ได้")
    void otherUser_cannotCancelSomeoneElsesOrder() throws Exception {
        int aliceOrderId = placeOrder(login(alice), aliceAddress).get("id").asInt();

        MvcResult result = mockMvc.perform(patch("/api/v1/orders/" + aliceOrderId + "/cancel").cookie(login(bob)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
        assertThat(orderRepository.findById(aliceOrderId).orElseThrow().getStatus()).isEqualTo(Order.OrderStatus.PENDING);
    }

    // ───────────── addresses ─────────────

    @Test
    @DisplayName("[A01] Bob แก้ที่อยู่ของ Alice ไม่ได้ (ใส่ id ที่อยู่ของ Alice ใน /users/me/addresses)")
    void otherUser_cannotUpdateSomeoneElsesAddress() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/addresses/" + aliceAddress.getId()).cookie(login(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"ถูกแฮก\", \"recipientName\": \"Bob\", \"phone\": \"0899999999\" }"))
                .andExpect(deniedAccess());

        assertThat(addressRepository.findById(aliceAddress.getId()).orElseThrow().getName()).isEqualTo("บ้าน Alice");
    }

    @Test
    @DisplayName("[A01] Bob ลบที่อยู่ของ Alice ไม่ได้")
    void otherUser_cannotDeleteSomeoneElsesAddress() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/addresses/" + aliceAddress.getId()).cookie(login(bob)))
                .andExpect(deniedAccess());

        assertThat(addressRepository.findById(aliceAddress.getId())).isPresent();
    }

    @Test
    @DisplayName("[A01] Bob ตั้งที่อยู่ของ Alice เป็น default ของตัวเองไม่ได้")
    void otherUser_cannotSetSomeoneElsesAddressAsDefault() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/addresses/" + aliceAddress.getId() + "/default")
                        .cookie(login(bob)))
                .andExpect(deniedAccess());

        assertThat(addressRepository.findById(bobAddress.getId()).orElseThrow().getIsDefault()).isTrue();
    }

    // ───────────── notifications ─────────────

    @Test
    @DisplayName("[A01] Bob กดอ่าน notification ของ Alice ไม่ได้")
    void otherUser_cannotMarkSomeoneElsesNotificationAsRead() throws Exception {
        int aliceOrderId = placeOrder(login(alice), aliceAddress).get("id").asInt();
        Order aliceOrder = orderRepository.findById(aliceOrderId).orElseThrow();
        Notification note = notificationRepository.save(
                new Notification(alice, aliceOrder, "ออเดอร์จัดส่งแล้ว", "SHIPPED"));

        mockMvc.perform(patch("/api/v1/notifications/" + note.getId() + "/read").cookie(login(bob)));

        assertThat(notificationRepository.findById(note.getId()).orElseThrow().isRead()).isFalse();
    }

    // ───────────── order number ─────────────

    @Test
    @DisplayName("ลูกค้า 2 คนสั่งในวินาทีเดียวกัน ต้องสั่งได้ทั้งคู่")
    void twoOrdersInTheSameSecond_bothSucceed() throws Exception {
        Cookie aliceToken = login(alice);
        Cookie bobToken = login(bob);

        JsonNode first = placeOrder(aliceToken, aliceAddress);
        JsonNode second = placeOrder(bobToken, bobAddress);

        assertThat(second.get("orderNo").asText()).isNotEqualTo(first.get("orderNo").asText());
    }

    // ───────────── tokens ─────────────

    @Test
    @DisplayName("token ที่ถูกแก้ไขต้องได้ 401 ไม่ใช่ 500")
    void tamperedToken_isRejectedWith401() throws Exception {
        Cookie real = login(alice);
        String[] parts = real.getValue().split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        mockMvc.perform(get("/api/v1/orders").cookie(new Cookie("access_token", tampered)))
                .andExpect(status().isUnauthorized());
    }
}
