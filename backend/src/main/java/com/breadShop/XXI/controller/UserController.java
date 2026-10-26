package com.breadShop.XXI.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.Util.RequestInfo;
import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.customer.UsernameRequest;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.UserActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** /api/v1/users/me — ข้อมูลของคนที่ login อยู่ (ลูกค้าและ admin) */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final CurrentUser            currentUser;
    private final CustomerService        customerService;
    private final UserActivityLogService activityLogService;

    public UserController(CurrentUser currentUser, CustomerService customerService,
                          UserActivityLogService activityLogService) {
        this.currentUser        = currentUser;
        this.customerService    = customerService;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(toBody(currentUser.get(email))));
    }

    /** แก้ username ของตัวเอง */
    @PatchMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMe(
            @AuthenticationPrincipal String email, @Valid @RequestBody UsernameRequest request,
            HttpServletRequest httpRequest) {
        User user = currentUser.get(email);
        String oldUsername = user.getUsername();
        customerService.updateUsername(user.getId(), request.username());
        user.setUsername(request.username().trim());

        activityLogService.logSuccess(user, "UPDATE_PROFILE",
                RequestInfo.clientIp(httpRequest), RequestInfo.userAgent(httpRequest),
                "เปลี่ยน username: " + oldUsername + " → " + user.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("บันทึกสำเร็จ", toBody(user)));
    }

    private static Map<String, Object> toBody(User user) {
        return Map.of(
                "id",       user.getId(),
                "email",    user.getEmail(),
                "username", user.getUsername(),
                "role",     user.getRole());
    }
}
