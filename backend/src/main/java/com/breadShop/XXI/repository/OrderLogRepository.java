package com.breadShop.XXI.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.OrderLog;

// Repository สำหรับจัดการข้อมูล OrderLog โดยใช้ Spring Data JPA | reviewed by peak
public interface OrderLogRepository extends JpaRepository<OrderLog, Long> {

    @Query("""
        SELECT l FROM OrderLog l
        WHERE (:orderId   IS NULL OR l.order.id   = :orderId)
          AND (:orderNo   IS NULL OR l.orderNo LIKE %:orderNo%)
          AND (:newStatus IS NULL OR l.newStatus   = :newStatus)
          AND (:start     IS NULL OR l.createdAt  >= :start)
          AND (:end       IS NULL OR l.createdAt  <= :end)
        ORDER BY l.createdAt DESC
        """)
    Page<OrderLog> search(
        @Param("orderId")   Integer orderId,
        @Param("orderNo")   String orderNo,
        @Param("newStatus") String newStatus,
        @Param("start")     LocalDateTime start,
        @Param("end")       LocalDateTime end,
        Pageable pageable
    );
}
