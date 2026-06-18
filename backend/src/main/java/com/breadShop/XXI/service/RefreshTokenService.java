package com.breadShop.XXI.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.RefreshTokenRepository;

/*
 * Strategy ที่ใช้:
 *
 * 1. One-Token-Per-User:
 *    ตอน login ลบ token เก่าของ user คนนั้นก่อน แล้วค่อยสร้างใหม่
 *
 * 2. Token Rotation:
 *    ตอน refresh ลบ token เดิม แล้วสร้างใหม่
 *    → ถ้า token ถูกขโมยไป พอ rotate แล้วตัวเก่าใช้ไม่ได้
 *
 * 3. Token Hashing (S-5):
 *    เก็บ SHA-256 hash ใน DB แทน plain UUID
 *    → ถ้า DB รั่ว ผู้โจมตีไม่มี plain token
 *    → create() และ rotate() คืน plain String ให้ caller ส่งใส่ cookie
 *    → validate() รับ plain token แล้ว hash ก่อน lookup
 */
// เอาไว้สร้าง token ใหม่, validate token, rotate token, revoke token (ตอน logout) | reviewd by peak
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /**
     * สร้าง refresh token ใหม่สำหรับ user
     * @return plain token ที่ต้องส่งใส่ cookie (ไม่ใช่ hash)
     */
    @Transactional
    public String create(User user) {
        repo.deleteAllByUser(user);

        String plainToken = UUID.randomUUID().toString();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(sha256(plainToken)); // เก็บ hash ใน DB
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        repo.save(token);
        return plainToken;
    }

    /**
     * ตรวจสอบ plain token — hash ก่อน lookup
     * @param plainToken token จาก cookie
     * @return RefreshToken entity ที่ใช้งานได้
     */
    public RefreshToken validate(String plainToken) {
        RefreshToken rt = repo.findByTokenAndRevokedFalse(sha256(plainToken))
            .orElseThrow(() -> new RuntimeException("INVALID_REFRESH_TOKEN"));

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            rt.setRevoked(true);
            repo.save(rt);
            throw new RuntimeException("REFRESH_TOKEN_EXPIRED");
        }

        return rt;
    }

    /**
     * Token Rotation — ลบเก่า สร้างใหม่
     * @return plain token ใหม่ (ส่งใส่ cookie)
     */
    @Transactional
    public String rotate(RefreshToken oldToken) {
        repo.deleteByToken(oldToken.getToken()); // oldToken.getToken() เป็น hash อยู่แล้ว

        String plainToken = UUID.randomUUID().toString();
        RefreshToken newToken = new RefreshToken();
        newToken.setUser(oldToken.getUser());
        newToken.setToken(sha256(plainToken));
        newToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        repo.save(newToken);
        return plainToken;
    }

    /**
     * Revoke token (ตอน logout)
     */
    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        repo.save(token);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
