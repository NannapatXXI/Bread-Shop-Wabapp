package com.breadShop.XXI.service;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

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
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
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
    //  อ่าน token ที่หมดอายุ / เซ็นด้วย key อื่น
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("token หมดอายุ → อ่านแล้วได้ ExpiredJwtException (filter ใช้ตอบ 401 JWT_EXPIRED)")
    void readingAnExpiredToken_throwsExpiredJwtException() {
        JwtService expiredJwtService = new JwtService(TEST_SECRET, -1000L);
        String expiredToken = expiredJwtService.generateToken(mockUser("user@breadshop.com"));

        assertThatThrownBy(() -> jwtService.extractEmail(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("token ที่เซ็นด้วย secret อื่น → ปฏิเสธ (ปลอม token ไม่ได้)")
    void tokenSignedWithAnotherSecret_isRejected() {
        String otherSecret = java.util.Base64.getEncoder().encodeToString("another-secret-key-32-bytes-long!!".getBytes());
        String forged = new JwtService(otherSecret, EXPIRATION_MS).generateToken(mockUser("admin@breadshop.com"));

        assertThatThrownBy(() -> jwtService.extractEmail(forged))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  extractRole
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("extractRole — token มี role ROLE_ADMIN → คืน ROLE_ADMIN")
    void extractRole_withAdminAuthority_shouldReturnRoleAdmin() {
        UserDetails admin = User.withUsername("admin@breadshop.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();

        String token = jwtService.generateToken(admin);

        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("extractRole — token ไม่มี role เลย → default เป็น ROLE_USER")
    void extractRole_withNoAuthorities_shouldDefaultToRoleUser() {
        UserDetails user = mockUser("norole@breadshop.com");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_USER");
    }
}
