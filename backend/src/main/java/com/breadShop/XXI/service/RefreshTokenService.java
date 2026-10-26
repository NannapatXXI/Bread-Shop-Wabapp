package com.breadShop.XXI.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.RefreshTokenRepository;

/**
 * Refresh token (อายุ 7 วัน)
 *   - DB เก็บแค่ SHA-256 ของ token — DB รั่วก็เอาไปใช้ไม่ได้ ส่วนตัวจริงอยู่ใน cookie ของผู้ใช้
 *   - 1 user มีได้ 1 token: login ใหม่จะลบของเก่า (login ได้ทีละอุปกรณ์)
 *   - rotation: ทุกครั้งที่ refresh ตัวเก่าถูกลบแล้วออกตัวใหม่
 */
@Service
public class RefreshTokenService {

    private static final int TTL_DAYS = 7;

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /** login ใหม่ — ลบ token เก่าของ user แล้วออกใหม่ @return token ตัวจริงสำหรับใส่ cookie */
    @Transactional
    public String create(User user) {
        repo.deleteAllByUser(user);
        return issue(user);
    }

    /** @return token ใน DB ที่ยังใช้ได้ — ไม่เจอ/ถูก revoke → INVALID_REFRESH_TOKEN, หมดอายุ → REFRESH_TOKEN_EXPIRED */
    public RefreshToken validate(String plainToken) {
        RefreshToken token = repo.findByTokenAndRevokedFalse(sha256(plainToken))
                .orElseThrow(() -> new RuntimeException("INVALID_REFRESH_TOKEN"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setRevoked(true);
            repo.save(token);
            throw new RuntimeException("REFRESH_TOKEN_EXPIRED");
        }
        return token;
    }

    /** ลบตัวเก่า ออกตัวใหม่ @return token ตัวจริงตัวใหม่ */
    @Transactional
    public String rotate(RefreshToken oldToken) {
        repo.deleteByToken(oldToken.getToken());   // getToken() เป็น hash อยู่แล้ว
        return issue(oldToken.getUser());
    }

    /** logout */
    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        repo.save(token);
    }

    /** ทุกอุปกรณ์ต้อง login ใหม่ — ใช้ตอน reset รหัสผ่าน หรือบัญชีถูกแบน */
    @Transactional
    public void revokeAll(User user) {
        repo.deleteAllByUser(user);
    }

    private String issue(User user) {
        String plainToken = UUID.randomUUID().toString();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(sha256(plainToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(TTL_DAYS));
        repo.save(token);
        return plainToken;
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);   // JVM ทุกตัวต้องมี — ไม่ควรเกิด
        }
    }
}
