package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.ApiRequestLog;

// Repository สำหรับจัดการข้อมูล ApiRequestLog โดยใช้ Spring Data JPA | reviewed by peak 
public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {

    // group by endpoint + method → [endpoint, method, count, avgMs, successCount, errorCount]
    /**
     * ดึงสถิติของ API requests โดยจัดกลุ่มตาม endpoint และ method และคำนวณจำนวนครั้งที่เรียกใช้งาน, ค่าเฉลี่ยเวลาที่ใช้ในการประมวลผล, 
     * จำนวนครั้งที่สำเร็จ (status code < 400) และจำนวนครั้งที่ล้มเหลว (status code >= 400) โดยเรียงลำดับตามจำนวนครั้งที่เรียกใช้งานมากที่สุด
     * @return รายการของ Object[] ที่ประกอบด้วย [endpoint, method, count, avgMs, successCount, errorCount]
     */
    @Query("""
        SELECT a.endpoint, a.method, COUNT(a), AVG(a.durationMs),
               SUM(CASE WHEN a.statusCode < 400 THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.statusCode >= 400 THEN 1 ELSE 0 END)
        FROM ApiRequestLog a
        GROUP BY a.endpoint, a.method
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> getApiStats();

    // failed requests (status >= 400) newest first
    /**
     * ค้นหา ApiRequestLog ที่มี status code มากกว่าหรือเท่ากับค่าที่กำหนด และเรียงลำดับตามวันที่สร้าง (createdAt) จากใหม่ไปเก่า
     * @param statusCode ค่าของ status code ที่ต้องการค้นหา (เช่น 400 สำหรับ error)
     * @param pageable ข้อมูลการแบ่งหน้า (Pageable) สำหรับผลลัพธ์ที่ต้องการ
     * @return Page ของ ApiRequestLog ที่ตรงกับเงื่อนไขการค้นหา
     */
    Page<ApiRequestLog> findByStatusCodeGreaterThanEqualOrderByCreatedAtDesc(
        int statusCode, Pageable pageable);

    // error breakdown by status range → [range_label, count]
    /**
     * ดึงข้อมูลสรุปของ API requests ที่ล้มเหลว (status code >= 400) โดยจัดกลุ่มตามช่วงของ status code และนับจำนวนครั้งที่เกิดขึ้นในแต่ละช่วง
     * @return รายการของ Object[] ที่ประกอบด้วย [range_label, count] โดย range_label คือช่วงของ status code เช่น '5xx Server Error', '404 Not Found', '401 Unauthorized', '403 Forbidden', '4xx Client Error' และ count คือจำนวนครั้งที่เกิดขึ้นในแต่ละช่วง
     */
    @Query("""
        SELECT
            CASE
                WHEN a.statusCode >= 500 THEN '5xx Server Error'
                WHEN a.statusCode = 404  THEN '404 Not Found'
                WHEN a.statusCode = 401  THEN '401 Unauthorized'
                WHEN a.statusCode = 403  THEN '403 Forbidden'
                ELSE '4xx Client Error'
            END,
            COUNT(a)
        FROM ApiRequestLog a
        WHERE a.statusCode >= 400
        GROUP BY
            CASE
                WHEN a.statusCode >= 500 THEN '5xx Server Error'
                WHEN a.statusCode = 404  THEN '404 Not Found'
                WHEN a.statusCode = 401  THEN '401 Unauthorized'
                WHEN a.statusCode = 403  THEN '403 Forbidden'
                ELSE '4xx Client Error'
            END
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> getErrorBreakdown();

    // count requests per day for last N days → [date_str, count]
    /**
     * ดึงข้อมูลสถิติของ API requests ที่ล้มเหลว (status code >= 400) โดยนับจำนวนครั้งที่เกิดขึ้นในแต่ละวันตั้งแต่วันที่กำหนด (since) และจัดเรียงตามวันที่จากเก่าไปใหม่
     * @param since วันที่เริ่มต้นของช่วงเวลาที่ต้องการนับจำนวน requests (LocalDateTime) โดยจะนับเฉพาะ requests ที่เกิดขึ้นตั้งแต่วันนั้นเป็นต้นไป 
     * @return รายการของ Object[] ที่ประกอบด้วย [date_str, count] โดย date_str คือวันที่ในรูปแบบ 'YYYY-MM-DD' และ count คือจำนวนครั้งที่เกิดขึ้นในวันนั้น
     */
    @Query("""
        SELECT DATE(a.createdAt), COUNT(a)
        FROM ApiRequestLog a
        WHERE a.statusCode >= 400
          AND a.createdAt >= :since
        GROUP BY DATE(a.createdAt)
        ORDER BY DATE(a.createdAt) ASC
        """)
    List<Object[]> getErrorTrendSince(@Param("since") LocalDateTime since);

    long countByStatusCodeGreaterThanEqual(int statusCode);
}
