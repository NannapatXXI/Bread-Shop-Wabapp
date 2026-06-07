package com.breadShop.XXI.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.log.ApiStatResponse;
import com.breadShop.XXI.dto.log.AuditLogResponse;
import com.breadShop.XXI.dto.log.ErrorTrendPoint;
import com.breadShop.XXI.dto.log.FailedRequestResponse;
import com.breadShop.XXI.dto.log.LogSummaryResponse;
import com.breadShop.XXI.dto.log.LogTrendPoint;
import com.breadShop.XXI.dto.log.OrderLogResponse;
import com.breadShop.XXI.dto.log.SystemLogEntry;
import com.breadShop.XXI.dto.log.UserActivityLogResponse;
import com.breadShop.XXI.service.ApiRequestLogService;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.OrderLogService;
import com.breadShop.XXI.service.SystemLogService;
import com.breadShop.XXI.service.UserActivityLogService;

/**
 * LogController — /api/v1/admin/logs/**
 * ต้องการ ROLE_ADMIN (lock ด้วย SecurityConfig)
 */
@RestController
@RequestMapping("/api/v1/admin/logs")
public class LogController {

    private final UserActivityLogService activityService;
    private final AuditLogService        auditService;
    private final OrderLogService        orderLogService;
    private final SystemLogService       systemLogService;
    private final ApiRequestLogService   apiRequestLogService;

    public LogController(UserActivityLogService activityService, AuditLogService auditService,
                         OrderLogService orderLogService, SystemLogService systemLogService,
                         ApiRequestLogService apiRequestLogService) {
        this.activityService     = activityService;
        this.auditService        = auditService;
        this.orderLogService     = orderLogService;
        this.systemLogService    = systemLogService;
        this.apiRequestLogService = apiRequestLogService;
    }

    // ─── Summary ────────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<LogSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(new LogSummaryResponse(
            activityService.totalCount(),
            auditService.total(),
            activityService.countByAction("LOGIN"),
            activityService.countByAction("REGISTER"),
            activityService.countActiveUsersToday(),
            systemLogService.countByLevel("ERROR"),
            systemLogService.countByLevel("WARN"),
            apiRequestLogService.totalRequests(),
            apiRequestLogService.totalErrors()
        )));
    }

    // ─── System Logs (Application Log จาก file) ─────────────────
    @GetMapping("/system")
    public ResponseEntity<ApiResponse<Page<SystemLogEntry>>> systemLogs(
            @RequestParam(defaultValue = "")   String keyword,
            @RequestParam(defaultValue = "")   String level,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            systemLogService.readLogs(level, keyword, page, size)));
    }

    // ─── Log Trend (hourly วันนี้จาก file) ──────────────────────
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<LogTrendPoint>>> logTrend() {
        return ResponseEntity.ok(ApiResponse.ok(systemLogService.getTrendByHour()));
    }

    // ─── Error Trend (daily ย้อนหลัง N วัน จาก file) ────────────
    @GetMapping("/error-trend")
    public ResponseEntity<ApiResponse<List<ErrorTrendPoint>>> errorTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(systemLogService.getErrorTrendByDay(days)));
    }

    // ─── User Activity Logs ──────────────────────────────────────
    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<Page<UserActivityLogResponse>>> activityLogs(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            activityService.search(userId, action, status, keyword, start, end, page, size)));
    }

    // ─── Audit Logs ──────────────────────────────────────────────
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> auditLogs(
            @RequestParam(required = false) Integer actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            auditService.search(actorId, action, entityType, keyword, start, end, page, size)));
    }

    // ─── Order Logs ──────────────────────────────────────────────
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Page<OrderLogResponse>>> orderLogs(
            @RequestParam(required = false) Integer orderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderLogService.search(orderId, orderNo, status, start, end, page, size)));
    }

    // ─── API Monitoring — stats per endpoint ────────────────────
    @GetMapping("/api-stats")
    public ResponseEntity<ApiResponse<List<ApiStatResponse>>> apiStats() {
        return ResponseEntity.ok(ApiResponse.ok(apiRequestLogService.getApiStats()));
    }

    // ─── API Monitoring — failed requests ───────────────────────
    @GetMapping("/failed-requests")
    public ResponseEntity<ApiResponse<Page<FailedRequestResponse>>> failedRequests(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(apiRequestLogService.getFailedRequests(page, size)));
    }

    // ─── Error breakdown by status range (สำหรับ pie chart) ─────
    @GetMapping("/error-breakdown")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> errorBreakdown() {
        return ResponseEntity.ok(ApiResponse.ok(apiRequestLogService.getErrorBreakdown()));
    }
}
