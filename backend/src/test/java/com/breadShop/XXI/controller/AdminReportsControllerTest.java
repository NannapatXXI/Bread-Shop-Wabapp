package com.breadShop.XXI.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.breadShop.XXI.dto.dashboard.DashboardSummaryResponse;
import com.breadShop.XXI.dto.dashboard.SalesChartResponse;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.DashboardService;
import com.breadShop.XXI.service.OrderLogService;
import com.breadShop.XXI.service.SystemLogService;
import com.breadShop.XXI.service.UserActivityLogService;

@WebMvcTest({ DashboardController.class, LogController.class })
class AdminReportsControllerTest extends ControllerTestSupport {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";

    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private UserActivityLogService activityService;
    @MockitoBean private AuditLogService auditService;
    @MockitoBean private OrderLogService orderLogService;
    @MockitoBean private SystemLogService systemLogService;

    // ───────────── access ─────────────

    @Test
    void dashboardAndLogs_asNormalUser_return403() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/admin/dashboard/summary").cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/logs/summary").cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService, activityService, systemLogService);
    }

    @Test
    void dashboard_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }

    // ───────────── dashboard ─────────────

    @Test
    void summary_returnsEveryNumberFromService() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(dashboardService.getSummary()).thenReturn(new DashboardSummaryResponse(
                new BigDecimal("120.00"), 4, new BigDecimal("3500.00"), 61, 29, 3, 12.5, -4.0));

        mockMvc.perform(get("/api/v1/admin/dashboard/summary").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayRevenue").value(120.00))
                .andExpect(jsonPath("$.data.todayOrders").value(4))
                .andExpect(jsonPath("$.data.monthRevenue").value(3500.00))
                .andExpect(jsonPath("$.data.totalOrders").value(61))
                .andExpect(jsonPath("$.data.totalCustomers").value(29))
                .andExpect(jsonPath("$.data.cancelledOrders").value(3))
                .andExpect(jsonPath("$.data.monthRevenueChangePercent").value(12.5))
                .andExpect(jsonPath("$.data.totalOrdersChangePercent").value(-4.0));
    }

    @Test
    void sales_defaultsPeriodToWeek() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(dashboardService.getSalesChart("week")).thenReturn(new SalesChartResponse(List.of()));

        mockMvc.perform(get("/api/v1/admin/dashboard/sales").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(dashboardService).getSalesChart("week");
    }

    @Test
    void sales_passesRequestedPeriod() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(dashboardService.getSalesChart("year")).thenReturn(new SalesChartResponse(List.of()));

        mockMvc.perform(get("/api/v1/admin/dashboard/sales").param("period", "year").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(dashboardService).getSalesChart("year");
    }

    // ───────────── logs ─────────────

    @Test
    void logSummary_putsEachCountInItsOwnField() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(activityService.totalCount()).thenReturn(57L);
        when(auditService.total()).thenReturn(8L);
        when(activityService.countByAction("LOGIN")).thenReturn(45L);
        when(activityService.countByAction("REGISTER")).thenReturn(6L);
        when(activityService.countActiveUsersToday()).thenReturn(2L);
        when(systemLogService.countByLevel("ERROR")).thenReturn(1L);
        when(systemLogService.countByLevel("WARN")).thenReturn(24L);
        when(apiRequestLogService.totalRequests()).thenReturn(993L);
        when(apiRequestLogService.totalErrors()).thenReturn(65L);

        mockMvc.perform(get("/api/v1/admin/logs/summary").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActivity").value(57))
                .andExpect(jsonPath("$.data.totalAudit").value(8))
                .andExpect(jsonPath("$.data.loginCount").value(45))
                .andExpect(jsonPath("$.data.registerCount").value(6))
                .andExpect(jsonPath("$.data.activeUsersToday").value(2))
                .andExpect(jsonPath("$.data.systemErrorCount").value(1))
                .andExpect(jsonPath("$.data.systemWarnCount").value(24))
                .andExpect(jsonPath("$.data.totalApiRequests").value(993))
                .andExpect(jsonPath("$.data.totalApiErrors").value(65));
    }

    @Test
    void systemLogs_passesLevelKeywordAndPaging_inTheRightOrder() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(systemLogService.readLogs("ERROR", "timeout", 2, 50)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/logs/system")
                        .param("level", "ERROR").param("keyword", "timeout")
                        .param("page", "2").param("size", "50")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(systemLogService).readLogs("ERROR", "timeout", 2, 50);
    }

    @Test
    void activityLogs_parsesIsoDates_andForwardsFilters() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 2, 23, 59, 59);
        when(activityService.search(7, "LOGIN", "SUCCESS", "alice", start, end, 0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/logs/activity")
                        .param("userId", "7").param("action", "LOGIN").param("status", "SUCCESS")
                        .param("keyword", "alice")
                        .param("start", "2026-10-01T00:00:00").param("end", "2026-10-02T23:59:59")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(activityService).search(7, "LOGIN", "SUCCESS", "alice", start, end, 0, 20);
    }

    @Test
    void auditLogs_withoutFilters_passesNulls() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(auditService.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/logs/audit").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(auditService).search(null, null, null, null, null, null, 0, 20);
    }

    @Test
    void orderLogs_forwardsOrderNoAndStatus() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(orderLogService.search(isNull(), anyString(), anyString(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/logs/orders").param("orderNo", "ORD-1").param("status", "SHIPPED")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(orderLogService).search(null, "ORD-1", "SHIPPED", null, null, 0, 20);
    }

    @Test
    void errorTrend_defaultsToSevenDays() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(systemLogService.getErrorTrendByDay(7)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/logs/error-trend").cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(systemLogService).getErrorTrendByDay(7);
    }

    @Test
    void failedRequests_forwardsPaging() throws Exception {
        stubUser(1, ADMIN, "ADMIN");
        when(apiRequestLogService.getFailedRequests(1, 10)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/logs/failed-requests").param("page", "1").param("size", "10")
                        .cookie(adminToken(ADMIN)))
                .andExpect(status().isOk());

        verify(apiRequestLogService).getFailedRequests(1, 10);
    }

    @Test
    void activityLogs_withBadDate_returns400() throws Exception {
        stubUser(1, ADMIN, "ADMIN");

        mockMvc.perform(get("/api/v1/admin/logs/activity").param("start", "yesterday").cookie(adminToken(ADMIN)))
                .andExpect(status().isBadRequest());

        verify(activityService, org.mockito.Mockito.never())
                .search(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
