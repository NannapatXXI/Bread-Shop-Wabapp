package com.breadShop.XXI.scheduler;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.breadShop.XXI.repository.AuditLogRepository;
import com.breadShop.XXI.repository.UserActivityLogRepository;

/**
 * ลบ log ทางธุรกิจที่เก่าเกินกำหนด ไม่ให้ตารางโตไม่สิ้นสุด
 *   activity log → app.retention.activity-days (ค่าเริ่มต้น 90 วัน)
 *   audit log    → app.retention.audit-days    (ค่าเริ่มต้น 365 วัน — ใช้ตรวจย้อนหลังว่า admin ทำอะไร)
 *   order log    → ไม่ลบ เป็นประวัติของ order ที่ยังอยู่ในระบบ
 */
@Component
public class LogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);

    private final UserActivityLogRepository activityRepository;
    private final AuditLogRepository        auditRepository;
    private final int activityDays;
    private final int auditDays;

    public LogRetentionJob(UserActivityLogRepository activityRepository, AuditLogRepository auditRepository,
                           @Value("${app.retention.activity-days:90}") int activityDays,
                           @Value("${app.retention.audit-days:365}") int auditDays) {
        this.activityRepository = activityRepository;
        this.auditRepository    = auditRepository;
        this.activityDays       = activityDays;
        this.auditDays          = auditDays;
    }

    // ตี 4 ทุกวัน — หลัง backup (ตี 2) ข้อมูลที่ลบจึงยังอยู่ใน backup ของคืนนั้น
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Bangkok")
    public void purgeOldLogs() {
        LocalDateTime now = LocalDateTime.now();
        int activity = activityRepository.deleteOlderThan(now.minusDays(activityDays));
        int audit    = auditRepository.deleteOlderThan(now.minusDays(auditDays));
        if (activity > 0 || audit > 0) {
            log.info("Purged old logs: activity={}, audit={}", activity, audit);
        }
    }
}
