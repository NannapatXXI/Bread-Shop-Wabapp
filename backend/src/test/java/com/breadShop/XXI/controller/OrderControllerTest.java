package com.breadShop.XXI.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.service.OrderService;

@WebMvcTest(OrderController.class)
class OrderControllerTest extends ControllerTestSupport {

    private static final String ALICE = "alice@example.com";

    @MockitoBean
    private OrderService orderService;

    private OrderResponse order(int id, int userId, OrderStatus status) {
        return new OrderResponse(id, "ORD-" + id, userId, "Alice", "0800000000", "1 Road",
                "Bangkok", "Pathumwan", "Lumphini", "10330",
                new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40.00"),
                null, status, null, null, List.of(), LocalDateTime.of(2026, 10, 1, 9, 30));
    }

    private static final String VALID_BODY = """
            { "userId": 999, "addressId": 3, "items": [{ "productId": 11, "quantity": 2 }] }
            """;

    // ───────────── authentication ─────────────

    @Test
    void getMyOrders_withoutToken_returns401_andNeverTouchesService() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("กรุณาเข้าสู่ระบบ"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getMyOrders_withExpiredToken_returns401_JWT_EXPIRED() throws Exception {
        mockMvc.perform(get("/api/v1/orders").cookie(expiredToken(ALICE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("JWT_EXPIRED"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getMyOrders_withTokenSignedByAnotherKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders").cookie(forgedAdminToken(ALICE)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void getMyOrders_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders").cookie(new jakarta.servlet.http.Cookie("access_token", "abc.def")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void getMyOrders_tokenForUserMissingFromDb_returns401() throws Exception {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders").cookie(userToken("ghost@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User not found"));

        verifyNoInteractions(orderService);
    }

    // ───────────── GET /api/v1/orders ─────────────

    @Test
    void getMyOrders_returnsOnlyOrdersOfTheUserInTheToken() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderService.getByUserId(7)).thenReturn(List.of(order(100, 7, OrderStatus.PENDING)));

        mockMvc.perform(get("/api/v1/orders").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderNo").value("ORD-100"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        verify(orderService).getByUserId(7);
    }

    // ───────────── POST /api/v1/orders ─────────────

    @Test
    void create_ignoresUserIdFromBody_andUsesUserFromToken() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderService.createOrder(any())).thenReturn(order(101, 7, OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("สร้าง order สำเร็จ"))
                .andExpect(jsonPath("$.data.id").value(101));

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orderService).createOrder(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7);
        assertThat(captor.getValue().getAddressId()).isEqualTo(3);
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).getProductId()).isEqualTo(11);
        assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void create_withoutAddress_returns400_withThaiMessage() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(post("/api/v1/orders").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "items": [{ "productId": 11, "quantity": 1 }] }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("กรุณาเลือกที่อยู่จัดส่ง"));

        verify(orderService, never()).createOrder(any());
    }

    @Test
    void create_withEmptyCart_returns400() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(post("/api/v1/orders").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"addressId\": 3, \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("กรุณาเพิ่มสินค้าในตะกร้า"));

        verify(orderService, never()).createOrder(any());
    }

    @Test
    void create_withZeroQuantity_returns400_fromNestedValidation() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(post("/api/v1/orders").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"addressId\": 3, \"items\": [{ \"productId\": 11, \"quantity\": 0 }] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("จำนวนสินค้าต้องมากกว่า 0"));

        verify(orderService, never()).createOrder(any());
    }

    @Test
    void create_whenServiceRejects_passesStatusAndReasonThrough() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderService.createOrder(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "สินค้า Donut มีไม่พอ"));

        mockMvc.perform(post("/api/v1/orders").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("สินค้า Donut มีไม่พอ"));
    }

    // ───────────── PATCH /api/v1/orders/{id}/cancel ─────────────

    @Test
    void cancel_passesOrderIdAndTokenUserIdToService() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderService.cancelOrder(55, 7)).thenReturn(order(55, 7, OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/orders/55/cancel").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ยกเลิก order สำเร็จ"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(orderService).cancelOrder(55, 7);
    }

    @Test
    void cancel_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/55/cancel"))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).cancelOrder(anyInt(), anyInt());
    }

    // ───────────── GET /api/v1/orders/{id} ─────────────

    @Test
    void getById_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/55"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void getById_unknownOrder_returns404() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderService.getByIdForUser(404, 7))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        mockMvc.perform(get("/api/v1/orders/404").cookie(userToken(ALICE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void getById_asksServiceForTheLoggedInUsersOrderOnly() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderService.getByIdForUser(55, 7)).thenReturn(order(55, 7, OrderStatus.PENDING));

        mockMvc.perform(get("/api/v1/orders/55").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(55));

        // ต้องใช้ getByIdForUser (เช็คเจ้าของ) — ไม่ใช่ getById ของ admin ที่ไม่เช็ค
        verify(orderService).getByIdForUser(55, 7);
        verify(orderService, never()).getById(anyInt());
    }
}
