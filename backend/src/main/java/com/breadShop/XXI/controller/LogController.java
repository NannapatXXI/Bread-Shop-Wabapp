package com.breadShop.XXI.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.log.AuditLogResponse;
import com.breadShop.XXI.dto.log.LogSummaryResponse;
import com.breadShop.XXI.dto.log.OrderLogResponse;
import com.breadShop.XXI.dto.log.UserActivityLogResponse;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.OrderLogService;
import com.breadShop.XXI.service.UserActivityLogService;

/**
 * LogController — /api/v1/admin/logs/**
 * ต้องการ ROLE_ADMIN (lock ด้วย SecurityConfig)
 *
 * เฉพาะ log ทางธุรกิจ (activity / audit / order)
 * log ของระบบและสถิติ request/error ดูใน Grafana (Loki + Prometheus)
 * ดู docker-compose.monitoring.yml
 */
@RestController
@RequestMapping("/api/v1/admin/logs")
public class LogController {

    private final UserActivityLogService activityService;
    private final AuditLogService        auditService;
    private final OrderLogService        orderLogService;

    public LogController(UserActivityLogService activityService, AuditLogService auditService,
                         OrderLogService orderLogService) {
        this.activityService = activityService;
        this.auditService    = auditService;
        this.orderLogService = orderLogService;
    }

    // ─── Summary ────────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<LogSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(new LogSummaryResponse(
            activityService.totalCount(),
            auditService.total(),
            activityService.countByAction("LOGIN"),
            activityService.countByAction("REGISTER"),
            activityService.countActiveUsersToday()
        )));
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
}
