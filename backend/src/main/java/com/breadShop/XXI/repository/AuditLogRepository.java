package com.breadShop.XXI.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.AuditLog;
// Repository สำหรับจัดการข้อมูล AuditLog โดยใช้ Spring Data JPA | reviewed by peak
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
        SELECT l FROM AuditLog l
        WHERE (:actorId     IS NULL OR l.actor.id  = :actorId)
          AND (:action      IS NULL OR l.action     = :action)
          AND (:entityType  IS NULL OR l.entityType = :entityType)
          AND (:keyword     IS NULL OR l.actorName LIKE %:keyword%
                                    OR l.details   LIKE %:keyword%)
          AND (:start       IS NULL OR l.createdAt >= :start)
          AND (:end         IS NULL OR l.createdAt <= :end)
        ORDER BY l.createdAt DESC
        """)
    Page<AuditLog> search(
        @Param("actorId")    Integer actorId,
        @Param("action")     String action,
        @Param("entityType") String entityType,
        @Param("keyword")    String keyword,
        @Param("start")      LocalDateTime start,
        @Param("end")        LocalDateTime end,
        Pageable pageable
    );
}
