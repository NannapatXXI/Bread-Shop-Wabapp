package com.breadShop.XXI.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * ออกและอ่าน access token (JWT, HS256)
 * token เก็บ email (subject) + role — filter อ่าน role จาก token ได้เลยไม่ต้องยิง DB
 * อ่าน token ที่หมดอายุ/ลายเซ็นผิด → JJWT โยน exception (ExpiredJwtException / JwtException)
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long      expirationMs;

    /** @param base64Secret secret แบบ Base64 ยาวอย่างน้อย 32 bytes (HS256) */
    public JwtService(@Value("${app.jwtSecret}") String base64Secret,
                      @Value("${app.jwtExpirationMs}") long expirationMs) {
        this.signingKey   = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails user) {
        Date now = new Date();
        return Jwts.builder()
                .claims(Map.of("roles", user.getAuthorities()))
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, Jwts.SIG.HS256)   // ระบุตรง ๆ — ไม่ปล่อยให้เลือกตามความยาว key
                .compact();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    /** role แรกใน token เช่น "ROLE_ADMIN" — ถ้าไม่มีถือเป็น ROLE_USER */
    public String extractRole(String token) {
        // claim "roles" ถูกเก็บเป็น [{authority=ROLE_USER}]
        if (parse(token).get("roles") instanceof List<?> roles
                && !roles.isEmpty()
                && roles.get(0) instanceof Map<?, ?> role
                && role.get("authority") != null) {
            return role.get("authority").toString();
        }
        return "ROLE_USER";
    }

    /** ตรวจลายเซ็น + วันหมดอายุ แล้วคืน claims */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
