package com.breadShop.XXI.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;

/**
 * [Claude] UserController — ข้อมูล user ทั่วไป
 * path: /api/v1/users/**
 *
 * หมายเหตุ: endpoint /me ที่นี่ซ้ำกับ AuthController.me()
 * TODO: ควร consolidate ให้เหลือแค่ที่เดียว (AuthController /api/v1/auth/me)
 * เก็บไว้ก่อนเพื่อไม่ให้ frontend พัง
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * GET /api/v1/users/me
     * คืนข้อมูล user ปัจจุบัน — ดึงจาก JWT token
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @AuthenticationPrincipal String email) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "id",       user.getId(),
                "email",    user.getEmail(),
                "username", user.getUsername(),
                "role",     user.getRole()
        )));
    }
}
