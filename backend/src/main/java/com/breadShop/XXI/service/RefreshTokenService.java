package com.breadShop.XXI.service;

import java.time.LocalDateTime;
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
 *    → user login 100 ครั้ง ใน DB ก็มีแค่ 1 row เสมอ
 *
 * 2. Token Rotation:
 *    ตอน refresh ลบ token เดิม แล้วสร้างใหม่ให้เลย
 *    → Security: ถ้า token ถูกขโมยไป พอ rotate แล้วตัวเก่าใช้ไม่ได้
 *    → ผลพลอยได้: DB ไม่สะสม token เก่า
 *
 * 3. Scheduled Cleanup (RefreshTokenCleanupJob):
 *    ทำงานทุกตี 3 ลบ token ที่ expired + revoked แล้ว
 *    → safety net กรณีมี token ค้างอยู่ (เช่น google login)
 */
//สำหรับการจัดการ refresh token โดยใช้กลยุทธ์ One-Token-Per-User และ Token Rotation รวมถึงการทำความสะอาด token ที่หมดอายุและถูกเพิกถอนแล้ว | reviewd by peak
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /**
     * สร้าง refresh token ใหม่
     * - ลบ token เก่าทั้งหมดของ user คนนี้ก่อน (One-Token-Per-User)
     * - แล้วค่อยสร้างใหม่
     */
    /**
     * สร้าง refresh token ใหม่สำหรับ user ที่กำหนด 
     * @param user ผู้ใช้ที่ต้องการสร้าง refresh token ให้
     * @return refresh token ใหม่ที่ถูกสร้างและบันทึกในฐานข้อมูล
     */
    @Transactional
    public RefreshToken create(User user) {
        // [One-Token-Per-User] ลบ token เก่าทั้งหมดของ user ก่อน
        repo.deleteAllByUser(user);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return repo.save(token);
    }

    /**
     * ตรวจสอบ token ว่ายังใช้ได้อยู่ไหม
     * - ถ้า revoked หรือ ไม่เจอ → throw exception
     * - ถ้า expired → mark revoked แล้ว throw exception
     */
    /**
     * ตรวจสอบความถูกต้องของ  token ที่ได้รับมา ว่า revoked หรือ expired
     * @param token refresh token ที่ต้องการตรวจสอบ
     * @return refresh token ที่ถูกต้องและยังไม่หมดอายุ
     */
    public RefreshToken validate(String token) {
        RefreshToken rt = repo.findByTokenAndRevokedFalse(token)
            .orElseThrow(() -> new RuntimeException("INVALID_REFRESH_TOKEN"));

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            rt.setRevoked(true);
            repo.save(rt);
            throw new RuntimeException("REFRESH_TOKEN_EXPIRED");
        }

        return rt;
    }

    /**
     * [Token Rotation] ลบ token เดิม แล้วสร้างใหม่ให้ user
     *
     * เรียกตอน POST /auth/refresh
     * เหตุผล: ถ้า token เดิมถูกขโมย พอ rotate แล้ว token นั้นใช้ไม่ได้อีก
     */
    /**
     * ทำการหมุนเวียน (rotate) refresh token โดยลบ token เดิมและสร้างใหม่ให้กับผู้ใช้เดียวกัน
     * @param oldToken refresh token เดิมที่ต้องการหมุนเวียน
     * @return refresh token ใหม่ที่ถูกสร้างและบันทึกในฐานข้อมูล
     */
    @Transactional
    public RefreshToken rotate(RefreshToken oldToken) {
        // ลบตัวเก่า
        repo.deleteByToken(oldToken.getToken());

        // สร้างตัวใหม่ให้ user คนเดิม
        RefreshToken newToken = new RefreshToken();
        newToken.setUser(oldToken.getUser());
        newToken.setToken(UUID.randomUUID().toString());
        newToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        return repo.save(newToken);
    }

    /**
     * Revoke token (ตอน logout)
     * token ที่ revoked แล้วจะถูก Cleanup Job ลบในรอบถัดไป
     */
    /**
     * ทำการเพิกถอน (revoke) refresh token โดยตั้งค่า revoked เป็น true และบันทึกการเปลี่ยนแปลงในฐานข้อมูล Revoke token (ตอน logout)
     * @param token refresh token ที่ต้องการเพิกถอน
     */ 
    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        repo.save(token);
    }
}
