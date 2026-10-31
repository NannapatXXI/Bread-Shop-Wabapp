package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.notification.NotificationResponse;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.NotificationService;
import com.breadShop.XXI.service.SseService;

/** /api/v1/notifications — แจ้งเตือนสถานะ order ของคนที่ login อยู่ (ดึงย้อนหลัง + stream แบบ realtime) */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SseService          sseService;
    private final CustomerService     customerService;

    public NotificationController(NotificationService notificationService, SseService sseService,
                                  CustomerService customerService) {
        this.notificationService = notificationService;
        this.sseService          = sseService;
        this.customerService     = customerService;
    }

    /**
     * เปิดค้างไว้ — server ส่ง event "notification" มาเมื่อ admin เปลี่ยนสถานะ order
     * เปิดเกินเพดานต่อคน → 429 ไม่มี body (browser ขอ text/event-stream เขียน JSON error ให้ไม่ได้)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal String email) {
        return sseService.subscribe(customerService.getByEmail(email).getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAll(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok("", notificationService.getByUserId(customerService.getByEmail(email).getId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Integer id, @AuthenticationPrincipal String email) {
        notificationService.markAsRead(id, customerService.getByEmail(email).getId());
        return ResponseEntity.ok(ApiResponse.ok("อ่านแล้ว", null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal String email) {
        notificationService.markAllAsRead(customerService.getByEmail(email).getId());
        return ResponseEntity.ok(ApiResponse.ok("อ่านทั้งหมดแล้ว", null));
    }
}
