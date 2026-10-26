package com.breadShop.XXI.service;

import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.dto.LoginRequest;
import com.breadShop.XXI.dto.RegisterRequest;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.EmailOtpRepository;
import com.breadShop.XXI.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService
 * ทดสอบ register, login, resetPassword, sendOTP, verifyOTP
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailOtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private JwtService jwtService;
    @Mock private Mailservice mailService;
    @Mock private OtpService otpService;
    @Mock private UserActivityLogService activityLogService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("nannapat", "nannapat@breadshop.com", "hashed_pw");
        sampleUser.setRole("USER");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  registerUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerUser — ข้อมูลถูกต้อง → บันทึก user ลง DB และส่ง welcome email")
    void registerUser_withValidData_shouldSaveAndSendEmail() {
        RegisterRequest request = new RegisterRequest("nannapat", "nannapat@breadshop.com", "pass1234");

        when(userRepository.existsByUsername("nannapat")).thenReturn(false);
        when(userRepository.existsByEmail("nannapat@breadshop.com")).thenReturn(false);
        when(passwordEncoder.encode("pass1234")).thenReturn("hashed_pw");

        authService.registerUser(request);

        verify(userRepository).save(any(User.class));
        verify(mailService).sendWelcomeEmail("nannapat@breadshop.com", "nannapat");
    }

    @Test
    @DisplayName("registerUser — username ซ้ำ → throw IllegalArgumentException")
    void registerUser_withDuplicateUsername_shouldThrow() {
        RegisterRequest request = new RegisterRequest("nannapat", "new@breadshop.com", "pass1234");
        when(userRepository.existsByUsername("nannapat")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USERNAME_EXISTS");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerUser — email ซ้ำ → throw IllegalArgumentException")
    void registerUser_withDuplicateEmail_shouldThrow() {
        RegisterRequest request = new RegisterRequest("newuser", "nannapat@breadshop.com", "pass1234");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("nannapat@breadshop.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EMAIL_EXISTS");

        verify(userRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  loginUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginUser — credentials ถูกต้อง → คืน AuthenticationResponse")
    void loginUser_withValidCredentials_shouldReturnTokens() {
        LoginRequest request = new LoginRequest("nannapat@breadshop.com", "pass1234");

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("nannapat@breadshop.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(userRepository.findByEmail("nannapat@breadshop.com"))
                .thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(userDetails)).thenReturn("access_token_xyz");
        when(refreshTokenService.create(sampleUser)).thenReturn("refresh_token_xyz");

        AuthenticationResponse response = authService.loginUser(request);

        assertThat(response.accessToken()).isEqualTo("access_token_xyz");
        assertThat(response.refreshToken()).isEqualTo("refresh_token_xyz");
        assertThat(response.username()).isEqualTo("nannapat");
        assertThat(response.email()).isEqualTo("nannapat@breadshop.com");
    }

    @Test
    @DisplayName("loginUser — รหัสถูกแต่บัญชีถูกแบน → ACCOUNT_DISABLED และไม่ออก token")
    void loginUser_bannedAccountWithCorrectPassword_getsNoTokens() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("nannapat@breadshop.com");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByEmail("nannapat@breadshop.com")).thenReturn(Optional.of(sampleUser));
        sampleUser.setActive(false);

        assertThatThrownBy(() -> authService.loginUser(new LoginRequest("nannapat@breadshop.com", "pass1234")))
                .hasMessage("ACCOUNT_DISABLED");

        verify(jwtService, never()).generateToken(any());
        verify(refreshTokenService, never()).create(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  resetPassword
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetPassword — token ถูกต้อง → เข้ารหัส password ใหม่และ invalidate token")
    void resetPassword_withValidToken_shouldUpdatePassword() {
        when(otpService.validateResetToken("valid_token"))
                .thenReturn("nannapat@breadshop.com");
        when(userRepository.findByEmail("nannapat@breadshop.com"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.encode("newPass123")).thenReturn("new_hashed_pw");

        authService.resetPassword("valid_token", "newPass123");

        assertThat(sampleUser.getPassword()).isEqualTo("new_hashed_pw");
        verify(userRepository).save(sampleUser);
        verify(otpService).invalidateToken("valid_token");
    }

    @Test
    @DisplayName("resetPassword — ยกเลิก refresh token ทุกตัว → ทุกอุปกรณ์ (รวมคนที่ขโมยบัญชี) ต้อง login ใหม่")
    void resetPassword_revokesEverySessionOfThatUser() {
        when(otpService.validateResetToken("valid_token")).thenReturn("nannapat@breadshop.com");
        when(userRepository.findByEmail("nannapat@breadshop.com")).thenReturn(Optional.of(sampleUser));

        authService.resetPassword("valid_token", "newPass123");

        verify(refreshTokenService).revokeAll(sampleUser);
    }

    @Test
    @DisplayName("resetPassword — ไม่พบ user จาก token → throw RuntimeException")
    void resetPassword_whenUserNotFound_shouldThrow() {
        when(otpService.validateResetToken("token_x")).thenReturn("nobody@breadshop.com");
        when(userRepository.findByEmail("nobody@breadshop.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("token_x", "pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("USER_NOT_FOUND");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  verifyOtp
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyOtp — OTP ถูกต้อง → คืน token เดิมกลับ")
    void verifyOtp_withCorrectOtp_shouldReturnToken() {
        doNothing().when(otpService).verifyOtp("my_token", "123456");

        String result = authService.verifyOtp("my_token", "123456");

        assertThat(result).isEqualTo("my_token");
    }

    @Test
    @DisplayName("verifyOtp — OTP ผิด → throw IllegalArgumentException")
    void verifyOtp_withWrongOtp_shouldThrow() {
        doThrow(new IllegalArgumentException("OTP_INVALID"))
                .when(otpService).verifyOtp("my_token", "000000");

        assertThatThrownBy(() -> authService.verifyOtp("my_token", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OTP_INVALID");
    }

    @Test
    @DisplayName("verifyOtp — OTP ถูกล็อก (OtpService โยน RuntimeException) → แปลงเป็น IllegalArgumentException code เดิม")
    void verifyOtp_locked_isTranslatedToTheSameCode() {
        doThrow(new RuntimeException("OTP_LOCKED")).when(otpService).verifyOtp("my_token", "000000");

        assertThatThrownBy(() -> authService.verifyOtp("my_token", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OTP_LOCKED");
    }

    @Test
    @DisplayName("verifyOtp — error อื่น (เช่น DB) ห้ามถูกห่อเป็น 400 พร้อมข้อความภายใน")
    void verifyOtp_unexpectedError_isNotWrappedIntoAClientMessage() {
        RuntimeException dbError = new RuntimeException("Deadlock found when trying to get lock; table email_otp");
        doThrow(dbError).when(otpService).verifyOtp("my_token", "000000");

        assertThatThrownBy(() -> authService.verifyOtp("my_token", "000000"))
                .isSameAs(dbError); // ไปที่ handler RuntimeException → 500 "เกิดข้อผิดพลาดในระบบ"
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("verifyOtp — ไม่พิมพ์รหัส OTP ลง log ทั้งตอนถูกและตอนผิด")
    void verifyOtp_neverWritesTheOtpToTheLog(CapturedOutput output) {
        doNothing().when(otpService).verifyOtp("t1", "482915");
        doThrow(new RuntimeException("OTP_INVALID")).when(otpService).verifyOtp("t2", "731406");

        authService.verifyOtp("t1", "482915");
        assertThatThrownBy(() -> authService.verifyOtp("t2", "731406"));

        assertThat(output.getAll()).doesNotContain("482915").doesNotContain("731406");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("resetPassword — ไม่พิมพ์อีเมลผู้ใช้ลง log")
    void emailIsNotWrittenToTheLog(CapturedOutput output) {
        User user = new User("ghost", "private.person@example.com", "hash");
        when(userRepository.findByEmail("private.person@example.com")).thenReturn(Optional.of(user));
        when(otpService.validateResetToken("reset-t")).thenReturn("private.person@example.com");

        authService.resetPassword("reset-t", "newPassw0rd!");

        assertThat(output.getAll()).doesNotContain("private.person@example.com");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  loginUser — additional branches
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginUser — account เป็น Google login → throw IllegalArgumentException(GOOGLE_ACCOUNT)")
    void loginUser_whenGoogleAccount_shouldThrow() {
        sampleUser.setProvider("google");
        LoginRequest request = new LoginRequest("nannapat@breadshop.com", "pass1234");

        when(userRepository.findByEmail("nannapat@breadshop.com")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.loginUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GOOGLE_ACCOUNT");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("loginUser — authenticate สำเร็จแต่ไม่พบ user ใน DB → throw USER_NOT_FOUND")
    void loginUser_whenUserNotFoundAfterAuth_shouldThrow() {
        LoginRequest request = new LoginRequest("ghost@breadshop.com", "pass1234");

        when(userRepository.findByEmail("ghost@breadshop.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("ghost@breadshop.com")).thenReturn(Optional.empty());

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("ghost@breadshop.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        assertThatThrownBy(() -> authService.loginUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USER_NOT_FOUND");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  sendResetPasswordOtp
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendResetPasswordOtp — email มีในระบบ → สร้าง OTP และส่งอีเมล")
    void sendResetPasswordOtp_whenEmailExists_shouldGenerateAndSendOtp() {
        when(userRepository.findByEmail("nannapat@breadshop.com")).thenReturn(Optional.of(sampleUser));
        com.breadShop.XXI.dto.OtpResult otpResult =
                new com.breadShop.XXI.dto.OtpResult("otp_token_123", "654321");
        when(otpService.generateOtp("nannapat@breadshop.com", "RESET_PASSWORD")).thenReturn(otpResult);

        String token = authService.sendResetPasswordOtp("nannapat@breadshop.com");

        assertThat(token).isEqualTo("otp_token_123");
        verify(otpRepository).deleteByEmailAndPurposeAndUsedFalse("nannapat@breadshop.com", "RESET_PASSWORD");
        verify(mailService).sendOtpEmail("nannapat@breadshop.com", "654321");
    }

    @Test
    @DisplayName("sendResetPasswordOtp — ไม่พบ email → ตอบเหมือนปกติ (ได้ token) แต่ไม่สร้าง OTP และไม่ส่งเมล")
    void sendResetPasswordOtp_whenEmailNotFound_looksTheSameButDoesNothing() {
        when(userRepository.findByEmail("ghost@breadshop.com")).thenReturn(Optional.empty());

        String token = authService.sendResetPasswordOtp("ghost@breadshop.com");

        assertThat(token).matches("[0-9a-f-]{36}"); // รูปแบบเดียวกับ token จริง (UUID)
        verifyNoInteractions(otpService, otpRepository);
        verify(mailService, never()).sendOtpEmail(any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  refreshAccessToken
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refreshAccessToken — ไม่มี refresh_token cookie → throw RuntimeException(NO_REFRESH_TOKEN)")
    void refreshAccessToken_withNoCookie_shouldThrow() {
        assertThatThrownBy(() -> authService.refreshAccessToken(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("NO_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("refreshAccessToken — มี cookie ถูกต้อง → rotate token และคืน AuthenticationResponse ใหม่")
    void refreshAccessToken_withValidCookie_shouldRotateAndReturnNewTokens() {

        RefreshToken oldRt = mock(RefreshToken.class);
        when(oldRt.getUser()).thenReturn(sampleUser);

        UserDetails userDetails = mock(UserDetails.class);

        when(refreshTokenService.validate("old_plain_token")).thenReturn(oldRt);
        when(userDetailsService.loadUserByUsername(sampleUser.getEmail())).thenReturn(userDetails);
        when(refreshTokenService.rotate(oldRt)).thenReturn("new_plain_token");
        when(jwtService.generateToken(userDetails)).thenReturn("new_access_token");

        AuthenticationResponse response = authService.refreshAccessToken("old_plain_token");

        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.refreshToken()).isEqualTo("new_plain_token");
        assertThat(response.username()).isEqualTo(sampleUser.getUsername());
        assertThat(response.email()).isEqualTo(sampleUser.getEmail());
    }

    @Test
    @DisplayName("refreshAccessToken — ผู้ใช้ถูกแบนหลัง login → ACCOUNT_DISABLED, ไม่ออก token ใหม่, ทิ้ง refresh token")
    void refreshAccessToken_bannedUser_isRefusedAndLoggedOutEverywhere() {
        RefreshToken oldRt = mock(RefreshToken.class);
        when(oldRt.getUser()).thenReturn(sampleUser);
        when(refreshTokenService.validate("old_plain_token")).thenReturn(oldRt);
        sampleUser.setActive(false);

        assertThatThrownBy(() -> authService.refreshAccessToken("old_plain_token"))
                .hasMessage("ACCOUNT_DISABLED");

        verify(refreshTokenService).revokeAll(sampleUser);
        verify(refreshTokenService, never()).rotate(any());
        verify(jwtService, never()).generateToken(any());
    }
}
