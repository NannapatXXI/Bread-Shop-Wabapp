package com.breadShop.XXI.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.OrderLineService;
import com.breadShop.XXI.service.UserActivityLogService;

@WebMvcTest({ OrderLineController.class, UserController.class })
class OrderLineAndUserControllerTest extends ControllerTestSupport {

    private static final String ALICE = "alice@example.com";

    @MockitoBean private OrderLineService orderLineService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private UserActivityLogService activityLogService;

    private OrderLineResponse line(int id) {
        return new OrderLineResponse(id, 11, "Donut", "SKU-11", "uploads/11.jpg",
                new BigDecimal("20.00"), 2, BigDecimal.ZERO, new BigDecimal("40.00"));
    }

    // ───────────── OrderLineController ─────────────

    @Test
    void lines_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/5/lines"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderLineService);
    }

    @Test
    void lines_passesOrderIdAndReturnsLines() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderLineService.getByOrderId(5, 7)).thenReturn(List.of(line(1), line(2)));

        mockMvc.perform(get("/api/v1/orders/5/lines").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productName").value("Donut"))
                .andExpect(jsonPath("$.data[0].totalPrice").value(40.00));

        verify(orderLineService).getByOrderId(5, 7);
    }

    @Test
    void singleLine_passesOrderIdLineIdAndLoggedInUser() throws Exception {
        stubUser(7, ALICE, "USER");
        when(orderLineService.getById(5, 9, 7)).thenReturn(line(9));

        mockMvc.perform(get("/api/v1/orders/5/lines/9").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));

        verify(orderLineService).getById(5, 9, 7);
    }

    // ───────────── UserController (/api/v1/users/me — ใครก็ได้ที่ login แล้ว) ─────────────

    @Test
    void me_asUser_returnsProfileFromDb() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/users/me").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.email").value(ALICE))
                .andExpect(jsonPath("$.data.username").value("user7"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void me_worksForAdminToo() throws Exception {
        stubUser(1, "admin@example.com", "ADMIN");

        mockMvc.perform(get("/api/v1/users/me").cookie(adminToken("admin@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_changesUsername_andLogsActivity() throws Exception {
        User alice = stubUser(7, ALICE, "USER");

        mockMvc.perform(patch("/api/v1/users/me").with(json("{ \"username\": \" alice_new \" }")).cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice_new"));

        verify(customerService).updateUsername(7, " alice_new ");
        verify(activityLogService).logSuccess(eq(alice), eq("UPDATE_PROFILE"), anyString(), anyString(),
                eq("เปลี่ยน username: user7 → alice_new"));
    }

    @Test
    void updateMe_blankUsername_returns400_andChangesNothing() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(patch("/api/v1/users/me").with(json("{ \"username\": \"   \" }")).cookie(userToken(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("username ห้ามว่าง"));

        verifyNoInteractions(customerService, activityLogService);
    }
}
