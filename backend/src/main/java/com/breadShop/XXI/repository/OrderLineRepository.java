package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.dto.dashboard.CategoryRevenue;
import com.breadShop.XXI.dto.dashboard.TopProductStat;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLine;

// Repository สำหรับจัดการข้อมูล OrderLine โดยใช้ Spring Data JPA | reviewed by peak
public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {

    List<OrderLine> findByOrderId(Integer orderId);

    /**
     * ยอดขายรวมแยกตามหมวดสินค้าในช่วงเวลา ไม่นับ order ที่สถานะอยู่ใน excluded
     * (SUM ไม่เป็น null — total_price เป็น NOT NULL และทุกกลุ่มมีอย่างน้อย 1 แถว)
     */
    @Query("SELECT new com.breadShop.XXI.dto.dashboard.CategoryRevenue(l.product.category, SUM(l.totalPrice)) " +
           "FROM OrderLine l " +
           "WHERE l.order.createdAt BETWEEN :start AND :end " +
           "AND l.order.status NOT IN :excluded " +
           "GROUP BY l.product.category")
    List<CategoryRevenue> sumRevenueByCategory(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("excluded") List<Order.OrderStatus> excluded);

    /** สินค้าขายดีในช่วงเวลา เรียงจากจำนวนชิ้นที่ขายได้มากไปน้อย ไม่นับ order ที่สถานะอยู่ใน excluded */
    @Query("SELECT new com.breadShop.XXI.dto.dashboard.TopProductStat(" +
           "l.product.id, l.productName, SUM(l.quantity), SUM(l.totalPrice), l.product.imageUrl) " +
           "FROM OrderLine l WHERE l.order.createdAt BETWEEN :start AND :end " +
           "AND l.order.status NOT IN :excluded " +
           "GROUP BY l.product.id, l.productName, l.product.imageUrl " +
           "ORDER BY SUM(l.quantity) DESC")
    List<TopProductStat> getTopProductStats(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("excluded") List<Order.OrderStatus> excluded);
}
