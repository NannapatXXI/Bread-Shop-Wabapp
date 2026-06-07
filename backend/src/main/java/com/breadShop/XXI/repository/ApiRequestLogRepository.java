package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.ApiRequestLog;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {

    // group by endpoint + method → [endpoint, method, count, avgMs, successCount, errorCount]
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
    Page<ApiRequestLog> findByStatusCodeGreaterThanEqualOrderByCreatedAtDesc(
        int statusCode, Pageable pageable);

    // error breakdown by status range → [range_label, count]
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
