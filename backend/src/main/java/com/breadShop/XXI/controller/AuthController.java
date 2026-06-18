package com.breadShop.XXI.controller;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.CheckEmailRequest;
import com.breadShop.XXI.dto.LoginRequest;
import com.breadShop.XXI.dto.RegisterRequest;
import com.breadShop.XXI.dto.ResetPasswordRequest;
import com.breadShop.XXI.dto.UpdateProfileRequest;
import com.breadShop.XXI.dto.VerifyOtpRequest;
import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.AuthService;
import com.breadShop.XXI.service.GoogleAuthService;
import com.breadShop.XXI.service.Mailservice;
import com.breadShop.XXI.service.RefreshTokenService;
import com.breadShop.XXI.service.UserActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * [Claude] AuthController — Authentication & Authorization
 * path: /api/v1/auth/**
 *
 * Token flow:
 *   access_token  → cookie httpOnly, อายุ 15 นาที, path=/
 *   refresh_token → cookie httpOnly, อายุ 7 วัน, path=/api/v1/auth (จำกัดเฉพาะ /refresh)
 *
 * หมายเหตุ: endpoint /login /logout /refresh /google/callback คืน ResponseEntity<ApiResponse<T>>
 * เพราะต้องแนบ Set-Cookie header ไปพร้อมกัน ทำผ่าน factory method ของ ResponseEntity เท่านั้น
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Value("${app.secure-cookie:false}")
    private boolean secureCookie;

    private final AuthService             authService;
    private final GoogleAuthService       googleAuthService;
    private final UserRepository          userRepository;
    private final Mailservice             mailservice;
    private final RefreshTokenService     refreshTokenService;
    private final UserActivityLogService  activityLogService;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService,
                          UserRepository userRepository, Mailservice mailservice,
                          RefreshTokenService refreshTokenService,
                          UserActivityLogService activityLogService) {
        this.authService        = authService;
        this.googleAuthService  = googleAuthService;
        this.userRepository     = userRepository;
        this.mailservice        = mailservice;
        this.refreshTokenService = refreshTokenService;
        this.activityLogService = activityLogService;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
    private String userAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua != null ? ua : "";
    }

    // ═══════════════════════════════════════════════════════════════
    //  LOGIN / LOGOUT / REFRESH
    // ═══════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/auth/login
     * รับ email+password → ออก access_token + refresh_token เป็น httpOnly cookie
     * ไม่ return token ใน body เพราะ httpOnly cookie ปลอดภัยกว่า localStorage
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody LoginRequest loginRequest) {

        var tokens = authService.loginUser(loginRequest);

        ResponseCookie accessCookie = ResponseCookie.from("access_token", tokens.accessToken())
                .httpOnly(true).secure(secureCookie).path("/").maxAge(15 * 60).sameSite("Lax").build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
                .httpOnly(true).secure(secureCookie).path("/api/v1/auth")
                .maxAge(7 * 24 * 60 * 60).sameSite("Lax").build();

        // [Claude] ต้องใช้ ResponseEntity เพื่อแนบ Set-Cookie header
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.ok("เข้าสู่ระบบสำเร็จ"));
    }

    /**
     * POST /api/v1/auth/logout
     * revoke refresh_token ใน DB แล้วลบ cookie ทั้งสอง
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal String email,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) {
                    try {
                        RefreshToken rt = refreshTokenService.validate(cookie.getValue());
                        refreshTokenService.revoke(rt);
                    } catch (Exception e) {
                        // token หมดอายุหรือไม่มีอยู่ ไม่ต้องทำอะไร
                    }
                }
            }
        }

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user ->
                activityLogService.logSuccess(user, "LOGOUT", clientIp(request), userAgent(request), "ออกจากระบบ")
            );
        }

        ResponseCookie clearAccess = ResponseCookie.from("access_token", "")
                .httpOnly(true).secure(secureCookie).path("/").maxAge(0).sameSite("Lax").build();
        ResponseCookie clearRefresh = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(secureCookie).path("/api/v1/auth").maxAge(0).sameSite("Lax").build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .body(ApiResponse.ok("ออกจากระบบสำเร็จ"));
    }

    /**
     * POST /api/v1/auth/refresh
     * อ่าน refresh_token จาก cookie → ออก access_token ใหม่
     * ใช้ตอน axios interceptor ตรวจพบ 401
     */
    /**
     * POST /api/v1/auth/refresh
     * Token Rotation: validate เก่า → ลบเก่า → ออก access_token + refresh_token ใหม่
     * ทั้งสอง cookie ถูก set ใหม่พร้อมกัน
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request) {
        var tokens = authService.refreshAccessToken(request);

        ResponseCookie accessCookie = ResponseCookie.from("access_token", tokens.accessToken())
                .httpOnly(true).path("/").maxAge(15 * 60).build();

        // refresh token ใหม่ (หลัง rotate) — path จำกัดไว้แค่ /api/v1/auth เพื่อ security
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
                .httpOnly(true).path("/api/v1/auth").maxAge(7 * 24 * 60 * 60).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.ok("refresh สำเร็จ"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  USER INFO
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/auth/me
     * คืน id, email, username, roles ของ user ที่ login อยู่
     * frontend ใช้ใน AuthContext เพื่อ hydrate user state
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @AuthenticationPrincipal String email,
            Authentication authentication) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "id",       user.getId(),
                "email",    user.getEmail(),
                "username", user.getUsername(),
                "roles",    authentication.getAuthorities()
        )));
    }

    /**
     * PUT /api/v1/auth/me
     * แก้ไข username ของ user ปัจจุบัน
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMe(
            @AuthenticationPrincipal String email,
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String oldUsername = user.getUsername();
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername());
        }
        userRepository.save(user);

        activityLogService.logSuccess(user, "UPDATE_PROFILE",
                clientIp(httpRequest), userAgent(httpRequest),
                "เปลี่ยน username: " + oldUsername + " → " + user.getUsername());

        return ResponseEntity.ok(ApiResponse.ok("บันทึกสำเร็จ", Map.of(
                "id",       user.getId(),
                "email",    user.getEmail(),
                "username", user.getUsername()
        )));
    }

    /**
     * GET /api/v1/auth/profile
     * คืน email, username, role — สำหรับหน้าที่ไม่ต้องการ id
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile(
            @AuthenticationPrincipal String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "email",    user.getEmail(),
                "username", user.getUsername(),
                "role",     user.getRole()
        )));
    }

    // ═══════════════════════════════════════════════════════════════
    //  REGISTER
    // ═══════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/auth/register
     * สมัครสมาชิก — hash password ด้วย BCrypt ใน AuthService
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok(ApiResponse.ok("สมัครสมาชิกสำเร็จ"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  OTP / RESET PASSWORD
    // ═══════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/auth/send-OTP-mail
     * ส่ง OTP ไปยัง email — คืน token ที่ใช้ยืนยันใน verify-otp
     * token นี้ไม่ใช่ JWT แต่เป็น UUID สำหรับ identify session การ reset
     */
    @PostMapping("/send-OTP-mail")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendOtp(
            @RequestBody CheckEmailRequest request) {

        String token = authService.sendResetPasswordOtp(request.email());
        return ResponseEntity.ok(ApiResponse.ok("ส่ง OTP เรียบร้อย",
                Map.of("token", token)));
    }

    /**
     * POST /api/v1/auth/verify-otp
     * ตรวจสอบ OTP — ถ้าถูกต้องจะคืน token ใหม่สำหรับ reset password
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(
            @RequestBody VerifyOtpRequest request) {

        String token = authService.verifyOtp(request.token(), request.otp());
        return ResponseEntity.ok(ApiResponse.ok("OTP ถูกต้อง",
                Map.of("token", token)));
    }

    /**
     * POST /api/v1/auth/reset-password
     * เปลี่ยนรหัสผ่านใหม่ — ต้องมี token จาก verify-otp
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("เปลี่ยนรหัสผ่านสำเร็จ"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  GOOGLE LOGIN
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/auth/google
     * คืน URL สำหรับ redirect ไป Google consent screen
     */
    @GetMapping("/google")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleLogin() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("url", googleAuthService.getGoogleLoginUrl())));
    }

    /**
     * GET /api/v1/auth/google/callback
     * Google redirect มาที่นี่พร้อม code → แลก code กับ JWT แล้ว redirect ไป frontend
     * [Claude] endpoint นี้ทำ HTTP 302 redirect จึงไม่ใช้ ApiResponse
     */
    @GetMapping("/google/callback")
    public ResponseEntity<?> callback(@RequestParam("code") String code) {
        try {
            var tokens = googleAuthService.handleGoogleCallback(code);

            // [Fix] set ทั้ง access_token + refresh_token เหมือน login ปกติ
            ResponseCookie accessCookie = ResponseCookie.from("access_token", tokens.accessToken())
                    .httpOnly(true).secure(secureCookie).path("/").maxAge(15 * 60).sameSite("Lax").build();

            ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
                    .httpOnly(true).secure(secureCookie).path("/api/v1/auth")
                    .maxAge(7 * 24 * 60 * 60).sameSite("Lax").build();

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.SET_COOKIE, accessCookie.toString());
            headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            headers.setLocation(URI.create("http://localhost:3000/home"));

            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Google Login Error: " + e.getMessage()));
        }
    }
}
