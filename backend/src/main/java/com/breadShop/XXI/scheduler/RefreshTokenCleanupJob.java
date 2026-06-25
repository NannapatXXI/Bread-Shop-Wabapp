package com.breadShop.XXI.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.repository.RefreshTokenRepository;

/**
 * Cleanup Job — safety net สุดท้าย
 *
 * Token Rotation ใน RefreshTokenService จัดการ token ส่วนใหญ่แล้ว
 * Job นี้คือ safety net สำหรับกรณีพิเศษ เช่น
 *   - token ที่ถูก revoke (logout) แต่ยังไม่ถึง expiresAt
 *   - token ของ Google Login ที่ไม่ผ่าน rotate flow
 *
 * รันทุกวัน ตี 3 (เวลา server ต่ำสุด)
 */
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository repo;

    public RefreshTokenCleanupJob(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        // ลบ token ที่หมดอายุ (ไม่ว่าจะ revoked หรือเปล่า)
        int deletedExpired = repo.deleteExpired(now);

        // ลบ token ที่ revoked แล้ว แต่ยังไม่ถึง expiresAt (เช่น logout ก่อนหมดอายุ)
        // Note: deleteExpired ครอบ revoked+expired แล้ว นี่ catch revoked+not-yet-expired
        int deletedRevoked = repo.deleteRevokedAndExpired(now);

        int total = deletedExpired + deletedRevoked;
        if (total > 0) {
            System.out.printf("🧹 [RefreshToken Cleanup] ลบ %d rows (expired: %d, revoked: %d)%n",
                total, deletedExpired, deletedRevoked);
        }
    }
}
