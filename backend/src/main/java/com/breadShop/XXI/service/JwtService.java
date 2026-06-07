package com.breadShop.XXI.service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// สำหรับการจัดการ JWT token reviewed by peak
@Service
public class JwtService {

    // Secret key ต้องเข้ารหัส Base64 และมีความยาว >= 256 bit (32 bytes)
    // ตัวอย่างใน application.properties:
    // app.jwtSecret=VGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNlY3JldCBrZXkgZm9yIEpXVA==
    @Value("${app.jwtSecret}")
    private String secretKey;

    // เวลา token หมดอายุ (ms)
    @Value("${app.jwtExpirationMs}")
    private long jwtExpirationMs;

    // ------------------- Extract Claim -------------------
    // ดึง email (username) จาก token
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // ฟังก์ชัน generic สำหรับดึงค่า claim ใดๆ
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    // ------------------- Generate Token -------------------
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // เก็บ role ไว้ใน claim
        claims.put("roles", userDetails.getAuthorities());

        // สร้าง JWT token
        return Jwts.builder()
                .setClaims(claims) // ใส่ custom claims
                .setSubject(userDetails.getUsername()) // ใส่ username/email
                .setIssuedAt(new Date()) // เวลาออก token
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // หมดอายุ
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // เซ็นด้วย key HS256
                .compact();
    }

    // ------------------- Validate Token -------------------
    public boolean isTokenValid(String token, UserDetails user) {
        String email = extractEmail(token);
        // ตรวจสอบว่าตรงกับ user และยังไม่หมดอายุ
        return email.equals(user.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // ------------------- Internal Helpers -------------------
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSigningKey()) // ใส่ key สำหรับตรวจสอบ signature
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        // แปลง Base64 string เป็น byte[]
        // สำคัญ: key ต้องมีความยาว >= 256 bit สำหรับ HS256
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
