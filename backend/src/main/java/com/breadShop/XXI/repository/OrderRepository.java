package com.breadShop.XXI.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.Order;

// Repository สำหรับจัดการข้อมูล Order โดยใช้ Spring Data JPA | reviewed by peak
public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    long countByUserId(Integer userId);

    List<Order> findByStatus(Order.OrderStatus status);

    // ── aggregate queries for DashboardService (ไม่โหลดทั้ง table) ──

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
           "WHERE o.createdAt BETWEEN :start AND :end AND o.status NOT IN :excluded")
    BigDecimal sumRevenueBetween(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 @Param("excluded") List<Order.OrderStatus> excluded);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    long countOrdersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(@Param("status") Order.OrderStatus status);

    // คืน [userId, orderCount] per user — ใช้แทน countByUserId() ใน loop (N+1)
    @Query("SELECT o.user.id, COUNT(o) FROM Order o GROUP BY o.user.id")
    List<Object[]> countOrdersGroupByUser();

    // ดึงเฉพาะ order ในช่วงเวลา + กรอง status — ใช้ใน getSalesChart() แทน findAll()
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status NOT IN :excluded")
    List<Order> findByDateRangeAndStatusNotIn(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("excluded") List<Order.OrderStatus> excluded);
}
