package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.notification.NotificationResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.NotificationService;
import com.breadShop.XXI.service.SseService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SseService sseService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService,
                                  SseService sseService,
                                  UserRepository userRepository) {
        this.notificationService = notificationService;
        this.sseService = sseService;
        this.userRepository = userRepository;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        User user = resolveUser(authentication);
        return sseService.subscribe(user.getId());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAll(Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok("", notificationService.getByUserId(user.getId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Integer id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok("อ่านแล้ว", null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        User user = resolveUser(authentication);
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("อ่านทั้งหมดแล้ว", null));
    }

    /**
     * ดึงข้อมูลผู้ใช้จาก Authentication และตรวจสอบว่าผู้ใช้นั้นมีอยู่ในฐานข้อมูลหรือไม่ ถ้าไม่มีจะโยน ResponseStatusException 404
     * @param authentication ข้อมูลการยืนยันตัวตนของผู้ใช้
     * @return User ที่เกี่ยวข้องกับ Authentication
     */
    private User resolveUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
