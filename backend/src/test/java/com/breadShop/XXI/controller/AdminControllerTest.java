package com.breadShop.XXI.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.customer.CustomerResponse;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.dto.product.ProductRequest;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.OrderService;
import com.breadShop.XXI.service.ProductService;

@WebMvcTest(AdminController.class)
class AdminControllerTest extends ControllerTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";

    @MockitoBean private ProductService productService;
    @MockitoBean private OrderService orderService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private AuditLogService auditLogService;

    private Product product(long id, String name) {
        Product p = new Product(name, new BigDecimal("25.00"), 12, "fresh", "uploads/x.jpg",
                ProductCategory.BREAD, LocalDate.of(2027, 1, 1));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private OrderResponse order(int id, OrderStatus status, String trackingNo) {
        return new OrderResponse(id, "ORD-" + id, 7, "Alice", "080", "1 Road", "BKK", "D", "S", "10330",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null, status, trackingNo,
                null, List.of(), LocalDateTime.now());
    }

    // ───────────── role rules ─────────────

    @Test
    void adminEndpoints_withoutToken_return401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/customers")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void adminEndpoints_asNormalUser_return403_andDoNothing() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/admin/orders").cookie(userToken(ALICE))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/products/5").cookie(userToken(ALICE))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/customers/8/role").with(json("{\"role\":\"ADMIN\"}")).cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/customers/8/active").with(json("{\"active\":false}")).cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService, productService, customerService, auditLogService);
    }

    // ───────────── products ─────────────

    @Test
    void createProduct_bindsMultipartFields_andWritesAuditLogAsCurrentAdmin() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");
        when(productService.createProduct(any())).thenReturn(product(30, "Croissant"));
        MockMultipartFile image = new MockMultipartFile("image", "c.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

        mockMvc.perform(multipart("/api/v1/admin/products").file(image)
                        .param("name", "Croissant").param("price", "25.00").param("stock", "12")
                        .param("category", "BREAD").param("expiryDate", "2027-01-01")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("เพิ่มสินค้าสำเร็จ"))
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.name").value("Croissant"));

        ArgumentCaptor<ProductRequest> captor = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).createProduct(captor.capture());
        ProductRequest sent = captor.getValue();
        assertThat(sent.getName()).isEqualTo("Croissant");
        assertThat(sent.getPrice()).isEqualByComparingTo("25.00");
        assertThat(sent.getStock()).isEqualTo(12);
        assertThat(sent.getCategory()).isEqualTo(ProductCategory.BREAD);
        assertThat(sent.getImage().getOriginalFilename()).isEqualTo("c.jpg");

        verify(auditLogService).log(eq(admin), eq("CREATE"), eq("Product"), eq("30"),
                isNull(), eq("Croissant"), eq("Add product: Croissant"));
    }

    @Test
    void createProduct_asUser_isRejectedBeforeServiceRuns() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(multipart("/api/v1/admin/products").param("name", "Hack").cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void createProduct_withNegativeStock_isRejectedByValidation() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(multipart("/api/v1/admin/products")
                        .param("name", "Bad").param("price", "10").param("stock", "-5")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void updateProduct_usesPutMultipart_andAuditsUpdate() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");
        when(productService.updateProduct(eq(30L), any())).thenReturn(product(30, "Croissant XL"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/admin/products/30")
                        .param("name", "Croissant XL").param("price", "30").param("stock", "5")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Croissant XL"));

        verify(auditLogService).log(eq(admin), eq("UPDATE"), eq("Product"), eq("30"),
                isNull(), eq("Croissant XL"), anyString());
    }

    @Test
    void deleteProduct_deletesAndAudits() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(delete("/api/v1/admin/products/30").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ลบสินค้าสำเร็จ"));

        verify(productService).deleteProduct(30L);
        verify(auditLogService).log(eq(admin), eq("DELETE"), eq("Product"), eq("30"),
                isNull(), isNull(), eq("Delete product id: 30"));
    }

    @Test
    void getProductById_returnsMappedProduct() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(productService.getProductById(30L)).thenReturn(product(30, "Croissant"));

        mockMvc.perform(get("/api/v1/admin/products/30").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Croissant"))
                .andExpect(jsonPath("$.data.stock").value(12));
    }

    // ───────────── orders ─────────────

    @Test
    void updateStatus_passesEnumAndTracking_andAuditsWithOrderNo() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");
        when(orderService.updateStatus(55, OrderStatus.SHIPPED, "TH123"))
                .thenReturn(order(55, OrderStatus.SHIPPED, "TH123"));

        mockMvc.perform(patch("/api/v1/admin/orders/55/status")
                        .with(json("{\"status\":\"SHIPPED\",\"trackingNo\":\"TH123\"}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNo").value("TH123"));

        verify(auditLogService).log(eq(admin), eq("CHANGE_ORDER_STATUS"), eq("Order"), eq("55"),
                isNull(), eq("SHIPPED"), eq("Order #ORD-55 → SHIPPED"));
    }

    @Test
    void updateStatus_withUnknownStatus_returns400_andChangesNothing() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/orders/55/status").with(json("{\"status\":\"TELEPORTED\"}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService, auditLogService);
    }

    @Test
    void getAllOrders_returnsEveryOrder() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(orderService.getAll()).thenReturn(List.of(order(1, OrderStatus.PENDING, null), order(2, OrderStatus.DELIVERED, null)));

        mockMvc.perform(get("/api/v1/admin/orders").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ───────────── customers (logic อยู่ใน CustomerServiceTest — ที่นี่เช็คว่าส่งต่อ + ลง audit) ─────────────

    @Test
    void customers_returnsServiceResult() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(customerService.getAll()).thenReturn(List.of(
                new CustomerResponse(7, "alice", ALICE, "USER", "credentials", LocalDateTime.now(), 3, true)));

        mockMvc.perform(get("/api/v1/admin/customers").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].orderCount").value(3));
    }

    @Test
    void updateRole_auditsOldAndNewRole() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");
        when(customerService.updateRole(8, "admin")).thenReturn("USER");

        mockMvc.perform(patch("/api/v1/admin/customers/8/role").with(json("{\"role\":\"admin\"}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(auditLogService).log(eq(admin), eq("CHANGE_ROLE"), eq("User"), eq("8"),
                eq("USER"), eq("ADMIN"), anyString());
    }

    @Test
    void updateRole_whenServiceRejects_returnsItsMessage_andDoesNotAudit() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(customerService.updateRole(8, "SUPERADMIN")).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "role ต้องเป็น USER หรือ ADMIN เท่านั้น"));

        mockMvc.perform(patch("/api/v1/admin/customers/8/role").with(json("{\"role\":\"SUPERADMIN\"}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("role ต้องเป็น USER หรือ ADMIN เท่านั้น"));

        verifyNoInteractions(auditLogService);
    }

    @Test
    void setActive_sendsRequestedState_andAuditsOldAndNew() throws Exception {
        User admin = stubUser(1, ADMIN, "ADMIN");
        when(customerService.setActive(8, false)).thenReturn(true);
        when(customerService.setActive(8, true)).thenReturn(false);

        mockMvc.perform(patch("/api/v1/admin/customers/8/active").with(json("{\"active\":false}")).cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ระงับบัญชีสำเร็จ"));
        verify(auditLogService).log(eq(admin), eq("BAN_USER"), eq("User"), eq("8"),
                eq("true"), eq("false"), anyString());

        mockMvc.perform(patch("/api/v1/admin/customers/8/active").with(json("{\"active\":true}")).cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ปลดระงับบัญชีสำเร็จ"));
    }

    @Test
    void setActive_withoutBody_returns400_andChangesNothing() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/customers/8/active").with(json("{}")).cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService, auditLogService);
    }

    @Test
    void updateUsername_delegatesToService() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/customers/8/username").with(json("{\"username\":\"  robert  \"}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("อัปเดต username สำเร็จ"));

        verify(customerService).updateUsername(8, "  robert  ");
    }

    @Test
    void updateUsername_blank_returns400_withMessage() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/customers/8/username").with(json("{\"username\":\"   \"}"))
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("username ห้ามว่าง"));

        verifyNoInteractions(customerService);
    }
}
