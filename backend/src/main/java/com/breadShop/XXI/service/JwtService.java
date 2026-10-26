package com.breadShop.XXI.service;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * ออกและอ่าน access token (JWT, HS256)
 * token เก็บ email (subject) + role — filter อ่าน role จาก token ได้เลยไม่ต้องยิง DB
 * อ่าน token ที่หมดอายุ/ลายเซ็นผิด → JJWT โยน exception (ExpiredJwtException / JwtException)
 */
@Service
public class JwtService {

    private final Key  signingKey;
    private final long expirationMs;

    /** @param base64Secret secret แบบ Base64 ยาวอย่างน้อย 32 bytes (HS256) */
    public JwtService(@Value("${app.jwtSecret}") String base64Secret,
                      @Value("${app.jwtExpirationMs}") long expirationMs) {
        this.signingKey   = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails user) {
        Date now = new Date();
        return Jwts.builder()
                .setClaims(Map.of("roles", user.getAuthorities()))
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
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
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
