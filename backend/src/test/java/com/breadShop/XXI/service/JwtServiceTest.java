package com.breadShop.XXI.service;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtService
 * ทดสอบการสร้าง token, ดึงข้อมูล, และ validate token
 */
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    // Base64 encoded secret key — ต้องยาวอย่างน้อย 256 bit (32 bytes) สำหรับ HS256
    private static final String TEST_SECRET =
            "dGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNlY3JldCBrZXkgZm9yIEpXVA==";
    private static final long EXPIRATION_MS = 900_000L; // 15 นาที

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", EXPIRATION_MS);
    }

    /** สร้าง UserDetails จำลอง */
    private UserDetails mockUser(String email) {
        return User.withUsername(email)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  generateToken
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken — ต้องคืน token ที่ไม่ว่างเปล่า")
    void generateToken_shouldReturnNonBlankToken() {
        String token = jwtService.generateToken(mockUser("user@breadshop.com"));
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("generateToken — token ต้องมี 3 ส่วนแบบ JWT (header.payload.signature)")
    void generateToken_shouldHaveThreeParts() {
        String token = jwtService.generateToken(mockUser("user@breadshop.com"));
        assertThat(token.split("\\.")).hasSize(3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  extractEmail
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("extractEmail — ต้องคืน email ที่ถูกต้อง")
    void extractEmail_shouldReturnCorrectEmail() {
        String email = "nannapat@breadshop.com";
        String token = jwtService.generateToken(mockUser(email));
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  isTokenValid
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid — token ยังไม่หมดอายุ + email ตรงกัน → true")
    void isTokenValid_withValidToken_shouldReturnTrue() {
        UserDetails user = mockUser("admin@breadshop.com");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid — email ไม่ตรงกับ token → false")
    void isTokenValid_withDifferentUser_shouldReturnFalse() {
        UserDetails owner = mockUser("owner@breadshop.com");
        UserDetails other = mockUser("other@breadshop.com");
        String token = jwtService.generateToken(owner);
        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid — token หมดอายุแล้ว → throw ExpiredJwtException")
    void isTokenValid_withExpiredToken_shouldThrowExpiredJwtException() {
        // สร้าง JwtService ใหม่ที่ตั้ง expiration เป็น -1000 ms (หมดอายุทันที)
        JwtService expiredJwtService = new JwtService();
        ReflectionTestUtils.setField(expiredJwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(expiredJwtService, "jwtExpirationMs", -1000L);

        UserDetails user = mockUser("user@breadshop.com");
        String expiredToken = expiredJwtService.generateToken(user);

        // library โยน ExpiredJwtException ก่อน isTokenValid จะ return ค่า
        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, user))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
