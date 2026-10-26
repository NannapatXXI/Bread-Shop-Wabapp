package com.breadShop.XXI.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.Util.RequestInfo;
import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.dto.LoginRequest;
import com.breadShop.XXI.dto.OtpResult;
import com.breadShop.XXI.dto.RegisterRequest;
import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.EmailOtpRepository;
import com.breadShop.XXI.repository.UserRepository;

/**
 * สมัคร, login, refresh token และลืมรหัสผ่าน (OTP ทางอีเมล)
 * error ส่งเป็น code (เช่น EMAIL_EXISTS) — GlobalExceptionHandler แปลเป็นข้อความภาษาไทย
 */
@Service
public class AuthService {

    private static final String RESET_PASSWORD = "RESET_PASSWORD";

    /** code ที่ OtpService โยน — ถือเป็นความผิดของผู้ใช้ (400) */
    private static final Set<String> OTP_ERROR_CODES = Set.of("OTP_EXPIRED", "OTP_INVALID", "OTP_LOCKED", "OTP_NOT_FOUND");

    private final UserRepository         userRepository;
    private final EmailOtpRepository     otpRepository;
    private final PasswordEncoder        passwordEncoder;
    private final AuthenticationManager  authenticationManager;
    private final UserDetailsService     userDetailsService;
    private final JwtService             jwtService;
    private final RefreshTokenService    refreshTokenService;
    private final OtpService             otpService;
    private final Mailservice            mailService;
    private final UserActivityLogService activityLogService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       EmailOtpRepository otpRepository, Mailservice mailService, OtpService otpService,
                       RefreshTokenService refreshTokenService, UserDetailsService userDetailsService,
                       UserActivityLogService activityLogService) {
        this.userRepository        = userRepository;
        this.otpRepository         = otpRepository;
        this.passwordEncoder       = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService    = userDetailsService;
        this.jwtService            = jwtService;
        this.refreshTokenService   = refreshTokenService;
        this.otpService            = otpService;
        this.mailService           = mailService;
        this.activityLogService    = activityLogService;
    }

    public void registerUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("USERNAME_EXISTS");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("EMAIL_EXISTS");
        }

        User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()));
        user.setRole("USER");
        userRepository.save(user);

        mailService.sendWelcomeEmail(request.email(), request.username());
        activityLogService.logSuccess(user, "REGISTER", RequestInfo.currentClientIp(), RequestInfo.currentUserAgent(),
                "Register with email: " + request.email());
    }

    public AuthenticationResponse loginUser(LoginRequest request) {
        // บัญชีที่สมัครผ่าน Google ไม่มีรหัสผ่าน — บอกให้ใช้ปุ่ม Google แทน "รหัสผิด" ที่ชวนงง
        userRepository.findByEmail(request.usernameOrEmail())
                .or(() -> userRepository.findByUsername(request.usernameOrEmail()))
                .filter(u -> "google".equalsIgnoreCase(u.getProvider()))
                .ifPresent(u -> { throw new IllegalArgumentException("GOOGLE_ACCOUNT"); });

        // รหัสผิด → BadCredentialsException (401)
        UserDetails userDetails = (UserDetails) authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.usernameOrEmail(), request.password())
        ).getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        // บอกว่าถูกแบนหลังรหัสผ่านถูกแล้วเท่านั้น — คนที่ไม่รู้รหัสเดาไม่ได้ว่าบัญชีนี้มีอยู่ (user enumeration)
        if (!user.isActive()) {
            throw new RuntimeException("ACCOUNT_DISABLED");
        }

        String accessToken  = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.create(user);

        activityLogService.logSuccess(user, "LOGIN", RequestInfo.currentClientIp(), RequestInfo.currentUserAgent(),
                "Login success for: " + user.getEmail());

        return new AuthenticationResponse(accessToken, refreshToken, user.getUsername(), user.getEmail());
    }

    /**
     * แลก refresh token เป็น token คู่ใหม่ — ตัวเก่าถูกลบ (rotation) ถ้าถูกขโมยไปก็ใช้ซ้ำไม่ได้
     * @param refreshToken ค่าจาก cookie refresh_token (null = ไม่มี cookie)
     */
    @Transactional
    public AuthenticationResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null) {
            throw new RuntimeException("NO_REFRESH_TOKEN");
        }

        RefreshToken oldToken = refreshTokenService.validate(refreshToken);
        User user = oldToken.getUser();

        // ถูกแบนหลัง login → ไม่ต่ออายุ และทิ้ง session ทุกอุปกรณ์
        if (!user.isActive()) {
            refreshTokenService.revokeAll(user);
            throw new RuntimeException("ACCOUNT_DISABLED");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newRefreshToken = refreshTokenService.rotate(oldToken);

        return new AuthenticationResponse(jwtService.generateToken(userDetails), newRefreshToken,
                user.getUsername(), user.getEmail());
    }

    // ─── ลืมรหัสผ่าน: sendResetPasswordOtp → verifyOtp → resetPassword ───

    /** @return token ที่ใช้คู่กับ OTP — อีเมลที่ไม่มีบัญชีก็ได้ token (ปลอม) เหมือนกัน */
    @Transactional
    public String sendResetPasswordOtp(String email) {
        if (userRepository.findByEmail(email).isEmpty()) {
            // ตอบเหมือนมีบัญชีทุกอย่าง — คนนอกเดาไม่ได้ว่าอีเมลนี้สมัครไว้ไหม
            // token นี้ไม่มีใน DB เอาไป verify ได้แค่ "OTP ไม่ถูกต้อง"
            return UUID.randomUUID().toString();
        }

        otpRepository.deleteByEmailAndPurposeAndUsedFalse(email, RESET_PASSWORD);
        OtpResult result = otpService.generateOtp(email, RESET_PASSWORD);
        mailService.sendOtpEmail(email, result.plainOtp());
        return result.token();
    }

    @Transactional
    public String verifyOtp(String token, String otp) {
        try {
            otpService.verifyOtp(token, otp);
        } catch (RuntimeException e) {
            // OTP ผิด/หมดอายุ/ถูกล็อก → 400 ส่วน error อื่น (เช่น DB) ปล่อยเป็น 500 ข้อความกลาง ๆ
            if (OTP_ERROR_CODES.contains(e.getMessage())) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            throw e;
        }
        return token;
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String email = otpService.validateResetToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // ทุกอุปกรณ์ต้อง login ใหม่ — ถ้ารีเซ็ตเพราะโดนขโมยบัญชี คนร้ายก็หลุดด้วย
        refreshTokenService.revokeAll(user);
        otpService.invalidateToken(token);
    }
}
