package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLine;

// Repository สำหรับจัดการข้อมูล OrderLine โดยใช้ Spring Data JPA | reviewed by peak
public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {

    List<OrderLine> findByOrderId(Integer orderId);

   /**
    * คำนวณยอดขายรวม (revenue) แยกตามหมวดหมู่สินค้าในช่วงเวลาที่กำหนด โดยจะไม่รวม order ที่มีสถานะอยู่ในรายการ excluded
    * @param start ช่วงเวลาที่เริ่มต้นสำหรับการคำนวณยอดขาย (เช่น 2024-01-01T00:00:00)
    * @param end ช่วงเวลาที่สิ้นสุดสำหรับการคำนวณยอดขาย (เช่น 2024-12-31T23:59:59)
    * @param excluded รายการสถานะของ order ที่ต้องการยกเว้นจากการคำนวณยอดขาย เช่น [CANCELED, PENDING] หากไม่ต้องการยกเว้นสถานะใดๆ ให้ส่งเป็น empty list หรือ null
    * @return รายการยอดขายรวมแยกตามหมวดหมู่สินค้าในรูปแบบ List<Object[]> โดยแต่ละ Object[] จะมี 2 ค่า คือ [category, totalRevenue] ซึ่ง category คือชื่อหมวดหมู่สินค้า และ totalRevenue คือยอดขายรวมของสินค้าที่อยู่ในหมวดหมู่นั้นในช่วงเวลาที่กำหนดและไม่ถูกยกเว้นตามสถานะ order
    */
    @Query("SELECT l.product.category, SUM(l.totalPrice) FROM OrderLine l " +
           "WHERE l.order.createdAt BETWEEN :start AND :end " +
           "AND l.order.status NOT IN :excluded " +
           "GROUP BY l.product.category")
    List<Object[]> sumRevenueByCategory(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("excluded") List<Order.OrderStatus> excluded);

    // คืน [productId, productName, sumQty, sumRevenue, imageUrl]
    // ใช้ใน DashboardService.getTopProducts()
    /**
     * ดึงข้อมูลสถิติยอดขายของสินค้าที่ขายดีที่สุด (top products) ในช่วงเวลาที่กำหนด โดยจะไม่รวม order ที่มีสถานะอยู่ในรายการ excluded โดยผลลัพธ์จะถูกจัดเรียงตามจำนวนสินค้าที่ขายได้ (quantity) จากมากไปน้อย
     * @param start ช่วงเวลาที่เริ่มต้นสำหรับการดึงข้อมูลสถิติยอดขาย (เช่น 2024-01-01T00:00:00)
     * @param end ช่วงเวลาที่สิ้นสุดสำหรับการดึงข้อมูลสถิติยอดขาย (เช่น 2024-12-31T23:59:59)
     * @param excluded รายการสถานะของ order ที่ต้องการยกเว้นจากการคำนวณยอดขาย เช่น [CANCELED, PENDING] หากไม่ต้องการยกเว้นสถานะใดๆ ให้ส่งเป็น empty list หรือ null
     * @return รายการสถิติยอดขายของสินค้าที่ขายดีที่สุดในรูปแบบ List<Object[]> โดยแต่ละ Object[] จะมี 5 ค่า คือ [productId, productName, sumQty, sumRevenue, imageUrl] ซึ่ง productId คือ ID ของสินค้า, productName คือชื่อของสินค้า, sumQty คือจำนวนสินค้าที่ขายได้ในช่วงเวลาที่กำหนดและไม่ถูกยกเว้นตามสถานะ order, sumRevenue คือยอดขายรวมของสินค้านั้นในช่วงเวลาที่กำหนดและไม่ถูกยกเว้นตามสถานะ order, และ imageUrl คือ URL ของรูปภาพสินค้า
     */
    @Query("SELECT l.product.id, l.productName, SUM(l.quantity), SUM(l.totalPrice), l.product.imageUrl " +
           "FROM OrderLine l WHERE l.order.createdAt BETWEEN :start AND :end " +
           "AND l.order.status NOT IN :excluded " +
           "GROUP BY l.product.id, l.productName, l.product.imageUrl " +
           "ORDER BY SUM(l.quantity) DESC")
    List<Object[]> getTopProductStats(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("excluded") List<Order.OrderStatus> excluded);
}
