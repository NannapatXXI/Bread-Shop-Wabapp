package com.breadShop.XXI.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.dto.LoginRequest;
import com.breadShop.XXI.dto.RegisterRequest;
import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.service.AuthService;
import com.breadShop.XXI.service.GoogleAuthService;
import com.breadShop.XXI.service.RefreshTokenService;
import com.breadShop.XXI.service.UserActivityLogService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTestSupport {

    private static final String ALICE = "alice@example.com";
    // RateLimitInterceptor นับตาม IP — ให้แต่ละเทสใช้ IP ไม่ซ้ำกัน จะได้ไม่กวนกัน
    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @MockitoBean private AuthService authService;
    @MockitoBean private GoogleAuthService googleAuthService;
    @MockitoBean private RefreshTokenService refreshTokenService;
    @MockitoBean private UserActivityLogService activityLogService;

    private static String freshIp() {
        return "10.0.0." + IP_SEQ.getAndIncrement();
    }

    /** IP ของ connection จริง (remoteAddr) — แต่ละเทสใช้ IP ของตัวเองจะได้ไม่ชน rate limit กัน */
    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String loginBody(String user, String pass) {
        return "{ \"usernameOrEmail\": \"" + user + "\", \"password\": \"" + pass + "\" }";
    }

    private static final AuthenticationResponse TOKENS =
            new AuthenticationResponse("ACCESS-123", "REFRESH-456", "alice", ALICE);

    // ───────────── login ─────────────

    @Test
    void login_setsHttpOnlyLaxCookies_withCorrectPathsAndLifetimes() throws Exception {
        when(authService.loginUser(any())).thenReturn(TOKENS);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("เข้าสู่ระบบสำเร็จ"))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(c -> assertThat(c)
                .startsWith("access_token=ACCESS-123").contains("Path=/;").contains("Max-Age=900")
                .contains("HttpOnly").contains("SameSite=Lax"));
        assertThat(cookies).anySatisfy(c -> assertThat(c)
                .startsWith("refresh_token=REFRESH-456").contains("Path=/api/v1/auth").contains("Max-Age=604800")
                .contains("HttpOnly").contains("SameSite=Lax"));
        // ห้ามมี token ใน body — ต้องอยู่ใน HttpOnly cookie เท่านั้น
        assertThat(result.getResponse().getContentAsString()).doesNotContain("ACCESS-123").doesNotContain("REFRESH-456");

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).loginUser(captor.capture());
        assertThat(captor.getValue().usernameOrEmail()).isEqualTo("alice");
        assertThat(captor.getValue().password()).isEqualTo("secret123");
    }

    @Test
    void login_wrongPassword_returns401_withThaiMessage_andNoCookies() throws Exception {
        when(authService.loginUser(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Username หรือ Password ไม่ถูกต้อง"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void login_blankFields_returns400_withoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("กรุณากรอก")));

        verifyNoInteractions(authService);
    }

    @Test
    void login_googleAccount_returnsTheCodeTheFrontendChecksFor() throws Exception {
        // frontend (login/page.js) แสดงข้อความพิเศษเมื่อ message === "GOOGLE_ACCOUNT"
        when(authService.loginUser(any())).thenThrow(new IllegalArgumentException("GOOGLE_ACCOUNT"));

        mockMvc.perform(post("/api/v1/auth/login").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("g@example.com", "whatever")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("GOOGLE_ACCOUNT"));
    }

    @Test
    void login_eleventhAttemptFromSameIp_isBlockedWith429_beforeReachingService() throws Exception {
        when(authService.loginUser(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        String ip = freshIp();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(fromIp(ip))
                            .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "guess" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").with(fromIp(ip))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "guess10")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false));

        verify(authService, times(10)).loginUser(any());

        // IP อื่นยังเข้าได้ตามปกติ
        mockMvc.perform(post("/api/v1/auth/login").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "again")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_spoofedXForwardedFor_doesNotResetTheLimit() throws Exception {
        when(authService.loginUser(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        String attacker = freshIp();

        // คนร้ายเปลี่ยน X-Forwarded-For ทุกครั้ง แต่ยิงจาก connection เดิม
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(fromIp(attacker)).header("X-Forwarded-For", "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "guess" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").with(fromIp(attacker)).header("X-Forwarded-For", "198.51.100.77")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "guess10")))
                .andExpect(status().isTooManyRequests());

        verify(authService, times(10)).loginUser(any());
    }

    // ───────────── logout ─────────────

    @Test
    void logout_revokesRefreshToken_logsActivity_andExpiresBothCookies() throws Exception {
        User alice = stubUser(7, ALICE, "USER");
        RefreshToken stored = new RefreshToken();
        when(refreshTokenService.validate("REFRESH-456")).thenReturn(stored);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(userToken(ALICE), new Cookie("refresh_token", "REFRESH-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ออกจากระบบสำเร็จ"))
                .andReturn();

        verify(refreshTokenService).revoke(stored);
        verify(activityLogService).logSuccess(eq(alice), eq("LOGOUT"), anyString(), anyString(), eq("ออกจากระบบ"));
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("access_token=;").contains("Max-Age=0"));
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("refresh_token=;").contains("Max-Age=0"));
    }

    @Test
    void logout_withInvalidRefreshToken_stillClearsCookies() throws Exception {
        when(refreshTokenService.validate("stale")).thenThrow(new RuntimeException("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", "stale")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(allOf(containsString("access_token=;"), containsString("Max-Age=0")))));

        verify(refreshTokenService, never()).revoke(any());
        verifyNoInteractions(activityLogService);
    }

    // ───────────── refresh ─────────────

    @Test
    void refresh_issuesNewCookies_withSameHardeningAsLogin() throws Exception {
        when(authService.refreshAccessToken(any())).thenReturn(TOKENS);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("refresh สำเร็จ"))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(c -> assertThat(c)
                .startsWith("access_token=ACCESS-123").contains("HttpOnly").contains("SameSite=Lax"));
        assertThat(cookies).anySatisfy(c -> assertThat(c)
                .startsWith("refresh_token=REFRESH-456").contains("Path=/api/v1/auth")
                .contains("HttpOnly").contains("SameSite=Lax"));
    }

    @Test
    void refresh_invalidToken_returns401_withThaiMessage() throws Exception {
        when(authService.refreshAccessToken(any())).thenThrow(new RuntimeException("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token ไม่ถูกต้อง กรุณาเข้าสู่ระบบใหม่"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refresh_bannedAccount_returns401_notServerError() throws Exception {
        when(authService.refreshAccessToken(any())).thenThrow(new RuntimeException("ACCOUNT_DISABLED"));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("บัญชีนี้ถูกระงับการใช้งาน"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ───────────── me / profile ─────────────

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsUserAndRolesFromToken() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/v1/auth/me").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.email").value(ALICE))
                .andExpect(jsonPath("$.data.roles[0].authority").value("ROLE_USER"));
    }

    @Test
    void me_worksForAdminToo() throws Exception {
        stubUser(1, "admin@example.com", "ADMIN");

        mockMvc.perform(get("/api/v1/auth/me").cookie(adminToken("admin@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].authority").value("ROLE_ADMIN"));
    }

    @Test
    void updateMe_changesUsername_savesAndLogs() throws Exception {
        User alice = stubUser(7, ALICE, "USER");

        mockMvc.perform(put("/api/v1/auth/me").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"username\": \"alice_new\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice_new"));

        assertThat(alice.getUsername()).isEqualTo("alice_new");
        verify(userRepository).save(alice);
        verify(activityLogService).logSuccess(eq(alice), eq("UPDATE_PROFILE"), anyString(), anyString(),
                eq("เปลี่ยน username: user7 → alice_new"));
    }

    @Test
    void updateMe_blankUsername_keepsOldName() throws Exception {
        User alice = stubUser(7, ALICE, "USER");

        mockMvc.perform(put("/api/v1/auth/me").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"username\": \"   \" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("user7"));

        assertThat(alice.getUsername()).isEqualTo("user7");
    }

    // ───────────── register / OTP / reset ─────────────

    @Test
    void register_validRequest_callsServiceWithSameData() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"alice\", \"email\": \"alice@example.com\", \"password\": \"secret123\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("สมัครสมาชิกสำเร็จ"));

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).registerUser(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("alice@example.com");
    }

    @Test
    void register_shortPasswordOrBadEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"alice\", \"email\": \"alice@example.com\", \"password\": \"short1\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"alice\", \"email\": \"not-an-email\", \"password\": \"secret123\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("รูปแบบ email ไม่ถูกต้อง"));

        verifyNoInteractions(authService);
    }

    @Test
    void register_duplicateUsername_returns400_withThaiMessage() throws Exception {
        doThrow(new IllegalArgumentException("USERNAME_EXISTS")).when(authService).registerUser(any());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"alice\", \"email\": \"alice@example.com\", \"password\": \"secret123\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("มีชื่อผู้ใช้นี้ในระบบแล้ว"));
    }

    @Test
    void sendOtp_returnsTokenFromService() throws Exception {
        when(authService.sendResetPasswordOtp("alice@example.com")).thenReturn("otp-token");

        mockMvc.perform(post("/api/v1/auth/send-OTP-mail").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"email\": \"alice@example.com\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("otp-token"));
    }

    @Test
    void sendOtp_bodyWithNestedEmailObject_returns400_notServerError() throws Exception {
        // หน้า forgot-password ของ frontend ส่ง {"email":{"email":"..."}} — ต้องได้ 400 ไม่ใช่ 500
        mockMvc.perform(post("/api/v1/auth/send-OTP-mail").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"email\": { \"email\": \"alice@example.com\" } }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void sendOtp_unknownEmail_returns400_withThaiMessage() throws Exception {
        when(authService.sendResetPasswordOtp("ghost@example.com")).thenThrow(new IllegalArgumentException("EMAIL_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/auth/send-OTP-mail").with(fromIp(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"email\": \"ghost@example.com\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ไม่พบ Email ในระบบ"));
    }

    @Test
    void verifyOtp_requiresSixDigits() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"t\", \"otp\": \"12ab\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OTP ต้องเป็นตัวเลข 6 หลัก"));

        verifyNoInteractions(authService);
    }

    @Test
    void verifyOtp_expired_returns400_withThaiMessage() throws Exception {
        when(authService.verifyOtp("t", "123456")).thenThrow(new IllegalArgumentException("OTP_EXPIRED"));

        mockMvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"t\", \"otp\": \"123456\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OTP หมดเวลา"));
    }

    @Test
    void verifyOtp_locked_returns400_withThaiMessage_notTheRawCode() throws Exception {
        when(authService.verifyOtp("t", "123456")).thenThrow(new IllegalArgumentException("OTP_LOCKED"));

        mockMvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"t\", \"otp\": \"123456\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OTP ถูกล็อก กรุณาขอ OTP ใหม่"));
    }

    @Test
    void verifyOtp_unexpectedServerError_returnsGeneric500_withoutInternalDetails() throws Exception {
        when(authService.verifyOtp("t", "123456"))
                .thenThrow(new RuntimeException("Deadlock found when trying to get lock; table email_otp"));

        mockMvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"t\", \"otp\": \"123456\" }"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("เกิดข้อผิดพลาดในระบบ"));
    }

    @Test
    void verifyOtp_success_returnsResetToken() throws Exception {
        when(authService.verifyOtp("t", "123456")).thenReturn("reset-token");

        mockMvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"t\", \"otp\": \"123456\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("reset-token"));
    }

    @Test
    void resetPassword_passesTokenAndNewPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"reset-token\", \"newPassword\": \"brandNew99\" }"))
                .andExpect(status().isOk());

        verify(authService).resetPassword("reset-token", "brandNew99");
    }

    @Test
    void resetPassword_tooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"reset-token\", \"newPassword\": \"abc\" }"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resetPassword(anyString(), anyString());
    }

    // ───────────── Google ─────────────

    @Test
    void googleLoginUrl_isPublic_andBindsARandomStateToThisBrowser() throws Exception {
        org.mockito.ArgumentCaptor<String> state = org.mockito.ArgumentCaptor.forClass(String.class);
        when(googleAuthService.getGoogleLoginUrl(state.capture())).thenReturn("https://accounts.google.com/o/oauth2/auth?x=1");

        MvcResult first = mockMvc.perform(get("/api/v1/auth/google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://accounts.google.com/o/oauth2/auth?x=1"))
                .andReturn();
        mockMvc.perform(get("/api/v1/auth/google")).andExpect(status().isOk());

        String firstState = state.getAllValues().get(0);
        assertThat(firstState).hasSizeGreaterThanOrEqualTo(40);           // 32 random bytes
        assertThat(state.getAllValues().get(1)).isNotEqualTo(firstState);   // สุ่มใหม่ทุกครั้ง
        assertThat(first.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("oauth_state=" + firstState)
                .contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/v1/auth/google");
    }

    private static final Cookie STATE_COOKIE = new Cookie("oauth_state", "state-abc");

    @Test
    void googleCallback_matchingState_setsCookies_clearsState_andRedirectsHome() throws Exception {
        when(googleAuthService.handleGoogleCallback("code-1")).thenReturn(TOKENS);

        mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "code-1").param("state", "state-abc")
                        .cookie(STATE_COOKIE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:3000/home"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(allOf(containsString("access_token=ACCESS-123"), containsString("HttpOnly")))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(allOf(startsWith("oauth_state=;"), containsString("Max-Age=0")))));
    }

    @Test
    void googleCallback_withoutStateCookie_isRejected_withoutLoggingAnyoneIn() throws Exception {
        // ลิงก์ callback ที่คนร้ายส่งมา (มี code ของบัญชีคนร้าย) — browser ของเหยื่อไม่มี cookie state
        mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "attacker-code").param("state", "state-abc"))
                .andExpect(status().isBadRequest())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, not(hasItem(containsString("access_token")))));

        verifyNoInteractions(googleAuthService);
    }

    @Test
    void googleCallback_withMismatchedOrMissingState_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "c").param("state", "other").cookie(STATE_COOKIE))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "c").cookie(STATE_COOKIE))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(googleAuthService);
    }

    @Test
    void googleCallback_failure_doesNotLeakInternalErrorDetails() throws Exception {
        when(googleAuthService.handleGoogleCallback("bad"))
                .thenThrow(new IllegalStateException("401 Unauthorized from https://oauth2.googleapis.com/token: invalid_client"));

        mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "bad").param("state", "state-abc")
                        .cookie(STATE_COOKIE))
                .andExpect(status().is5xxServerError())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.message", not(containsString("invalid_client"))));
    }
}
