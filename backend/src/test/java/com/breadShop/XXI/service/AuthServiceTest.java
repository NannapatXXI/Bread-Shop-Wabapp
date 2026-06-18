package com.breadShop.XXI.service;

import com.breadShop.XXI.dto.AuthenticationResponse;
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
 * ทดสอบ register, login, checkEmail, resetPassword, sendOTP, verifyOTP
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

    // ─────────────────────────────────────────────────────────────────────────
    //  checkEmail
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkEmail — email มีอยู่ในระบบ → ไม่ throw exception")
    void checkEmail_whenEmailExists_shouldNotThrow() {
        when(userRepository.findByEmail("nannapat@breadshop.com"))
                .thenReturn(Optional.of(sampleUser));

        assertThatCode(() -> authService.checkEmail(
                new com.breadShop.XXI.dto.CheckEmailRequest("nannapat@breadshop.com")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkEmail — email ไม่มีในระบบ → throw IllegalArgumentException")
    void checkEmail_whenEmailNotFound_shouldThrow() {
        when(userRepository.findByEmail("ghost@breadshop.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.checkEmail(
                new com.breadShop.XXI.dto.CheckEmailRequest("ghost@breadshop.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่มี Email");
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
        verify(otpService).invalidateToken("valid_token");
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
}
