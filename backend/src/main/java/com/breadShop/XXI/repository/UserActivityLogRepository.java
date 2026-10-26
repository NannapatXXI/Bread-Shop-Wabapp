package com.breadShop.XXI.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.entity.UserActivityLog;

// Repository สำหรับจัดการข้อมูล UserActivityLog โดยใช้ Spring Data JPA | reviewed by peak
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    @Query("""
        SELECT l FROM UserActivityLog l
        WHERE (:userId IS NULL OR l.user.id = :userId)
          AND (:action  IS NULL OR l.action = :action)
          AND (:status  IS NULL OR l.status = :status)
          AND (:keyword IS NULL OR l.username LIKE %:keyword% OR l.details LIKE %:keyword%)
          AND (:start   IS NULL OR l.createdAt >= :start)
          AND (:end     IS NULL OR l.createdAt <= :end)
        ORDER BY l.createdAt DESC
        """)
    Page<UserActivityLog> search(
        @Param("userId")  Integer userId,
        @Param("action")  String action,
        @Param("status")  String status,
        @Param("keyword") String keyword,
        @Param("start")   LocalDateTime start,
        @Param("end")     LocalDateTime end,
        Pageable pageable
    );

    long countByAction(String action);

    @Query("SELECT COUNT(DISTINCT l.user.id) FROM UserActivityLog l WHERE l.createdAt >= :since")
    long countActiveUsersSince(@Param("since") LocalDateTime since);

    /** ใช้โดย LogRetentionJob — ลบ log ที่เก่ากว่า cutoff */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserActivityLog l WHERE l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
