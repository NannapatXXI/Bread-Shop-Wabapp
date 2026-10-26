package com.breadShop.XXI.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.Util.RequestInfo;
import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.dto.CheckEmailRequest;
import com.breadShop.XXI.dto.LoginRequest;
import com.breadShop.XXI.dto.RegisterRequest;
import com.breadShop.XXI.dto.ResetPasswordRequest;
import com.breadShop.XXI.dto.VerifyOtpRequest;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.AuthService;
import com.breadShop.XXI.service.GoogleAuthService;
import com.breadShop.XXI.service.RefreshTokenService;
import com.breadShop.XXI.service.UserActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * /api/v1/auth/** — login, logout, refresh, สมัคร, ลืมรหัสผ่าน, Google  (ข้อมูลของตัวเองอยู่ที่ /api/v1/users/me)
 *
 * token ส่งเป็น httpOnly cookie เท่านั้น (JavaScript อ่านไม่ได้ ปลอดภัยกว่า localStorage):
 *   access_token  → 15 นาที, path=/
 *   refresh_token → 7 วัน,   path=/api/v1/auth (ส่งไปแค่ตอน refresh/logout)
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final String ACCESS_COOKIE  = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String STATE_COOKIE   = "oauth_state";
    private static final int ACCESS_MAX_AGE  = 15 * 60;
    private static final int REFRESH_MAX_AGE = 7 * 24 * 60 * 60;
    private static final int STATE_MAX_AGE   = 10 * 60;   // ต้องกลับจาก Google ภายใน 10 นาที
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService            authService;
    private final GoogleAuthService      googleAuthService;
    private final UserRepository         userRepository;
    private final RefreshTokenService    refreshTokenService;
    private final UserActivityLogService activityLogService;
    private final boolean secureCookie;
    private final String  frontendUrl;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService,
                          UserRepository userRepository, RefreshTokenService refreshTokenService,
                          UserActivityLogService activityLogService,
                          @Value("${app.secure-cookie:false}") boolean secureCookie,
                          @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.authService         = authService;
        this.googleAuthService   = googleAuthService;
        this.userRepository      = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.activityLogService  = activityLogService;
        this.secureCookie        = secureCookie;
        this.frontendUrl         = frontendUrl;
    }

    // ─── Login / Logout / Refresh ─────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody LoginRequest request) {
        var tokens = authService.loginUser(request);
        return ResponseEntity.ok()
                .headers(sessionCookies(tokens))
                .body(ApiResponse.ok("เข้าสู่ระบบสำเร็จ"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal String email,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest request) {

        if (refreshToken != null) {
            try {
                refreshTokenService.revoke(refreshTokenService.validate(refreshToken));
            } catch (RuntimeException e) {
                // token หมดอายุหรือไม่มีอยู่แล้ว — logout ต่อได้ตามปกติ
                log.debug("Logout with invalid refresh token: {}", e.getMessage());
            }
        }

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> activityLogService.logSuccess(user, "LOGOUT",
                    RequestInfo.clientIp(request), RequestInfo.userAgent(request), "ออกจากระบบ"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", 0));
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", "/api/v1/auth", 0));
        return ResponseEntity.ok().headers(headers).body(ApiResponse.ok("ออกจากระบบสำเร็จ"));
    }

    /** ใช้ refresh_token แลก token คู่ใหม่ (ตัวเก่าใช้ไม่ได้อีก — token rotation) */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        var tokens = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok()
                .headers(sessionCookies(tokens))
                .body(ApiResponse.ok("refresh สำเร็จ"));
    }

    // ─── Register / Forgot password ───────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("สมัครสมาชิกสำเร็จ"));
    }

    /** ลืมรหัสผ่าน 1/3: ส่ง OTP ไปที่อีเมล — คืน token (UUID) ที่ใช้คู่กับ OTP ในขั้นถัดไป */
    @PostMapping("/otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendOtp(@Valid @RequestBody CheckEmailRequest request) {
        String token = authService.sendResetPasswordOtp(request.email());
        return ResponseEntity.ok(ApiResponse.ok("ส่ง OTP เรียบร้อย", Map.of("token", token)));
    }

    /** ลืมรหัสผ่าน 2/3: ตรวจ OTP — คืน token ที่ใช้ตั้งรหัสใหม่ */
    @PostMapping("/otp/verification")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String token = authService.verifyOtp(request.token(), request.otp());
        return ResponseEntity.ok(ApiResponse.ok("OTP ถูกต้อง", Map.of("token", token)));
    }

    /** ลืมรหัสผ่าน 3/3: ตั้งรหัสผ่านใหม่ด้วย token จากขั้นที่ 2 */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("เปลี่ยนรหัสผ่านสำเร็จ"));
    }

    // ─── Google ────────────────────────────────────────────────────────

    /**
     * คืน URL หน้า Google พร้อม state สุ่ม — state เก็บใน cookie ของ browser นี้ด้วย
     * ตอน callback ต้องตรงกัน กันคนร้ายส่งลิงก์ callback ที่มี code ของบัญชีตัวเองมาให้กด (login CSRF)
     */
    @GetMapping("/google")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleLogin() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(STATE_COOKIE, state, "/api/v1/auth/google", STATE_MAX_AGE))
                .body(ApiResponse.ok(Map.of("url", googleAuthService.getGoogleLoginUrl(state))));
    }

    /** Google ส่งกลับมาพร้อม code → แลกเป็น token ของเรา → set cookie แล้ว redirect ไปหน้า home */
    @GetMapping("/google/callback")
    public ResponseEntity<?> callback(
            @RequestParam("code") String code,
            @RequestParam(name = "state", required = false) String state,
            @CookieValue(name = STATE_COOKIE, required = false) String expectedState) {

        String clearState = cookie(STATE_COOKIE, "", "/api/v1/auth/google", 0);

        if (!sameState(expectedState, state)) {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.SET_COOKIE, clearState)
                    .body(ApiResponse.error("คำขอเข้าสู่ระบบด้วย Google ไม่ถูกต้อง กรุณาเริ่มใหม่จากหน้า login"));
        }

        try {
            HttpHeaders headers = sessionCookies(googleAuthService.handleGoogleCallback(code));
            headers.add(HttpHeaders.SET_COOKIE, clearState);
            headers.setLocation(URI.create(frontendUrl + "/home"));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (Exception e) {
            if ("ACCOUNT_DISABLED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .header(HttpHeaders.SET_COOKIE, clearState)
                        .body(ApiResponse.error("บัญชีนี้ถูกระงับการใช้งาน"));
            }
            // รายละเอียด (เช่น client secret ผิด) เก็บใน log ฝั่ง server เท่านั้น
            log.error("Google login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("เข้าสู่ระบบด้วย Google ไม่สำเร็จ กรุณาลองใหม่"));
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private HttpHeaders sessionCookies(AuthenticationResponse tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, tokens.accessToken(), "/", ACCESS_MAX_AGE));
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, tokens.refreshToken(), "/api/v1/auth", REFRESH_MAX_AGE));
        return headers;
    }

    private String cookie(String name, String value, String path, int maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path(path).maxAge(maxAgeSeconds)
                .build().toString();
    }

    /** เทียบแบบเวลาคงที่ — เดา state ทีละตัวจากเวลาตอบไม่ได้ */
    private static boolean sameState(String expected, String actual) {
        return expected != null && actual != null && !expected.isEmpty()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
