package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.dashboard.CategorySalesResponse;
import com.breadShop.XXI.dto.dashboard.DashboardSummaryResponse;
import com.breadShop.XXI.dto.dashboard.SalesChartResponse;
import com.breadShop.XXI.dto.dashboard.TopProductResponse;
import com.breadShop.XXI.service.DashboardService;

/**
 * ทุก endpoint ใช้ window 30 วัน (ไม่ใช่เดือนปัจจุบัน)
 * เหตุผล: ถ้าใช้เดือนปัจจุบัน วันต้นเดือนจะเห็นข้อมูลน้อยมาก
 *
 * Endpoints:
 *   GET /summary        — ตัวเลขสรุปยอด (revenue, orders, customers)
 *   GET /sales          — กราฟยอดขายแยกตาม period (week/month/year)
 *   GET /category-sales — pie chart แยกตาม category
 *   GET /top-products   — สินค้าขายดี top 5
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** GET /api/v1/admin/dashboard/summary */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSummary()));
    }

    /**
     * GET /api/v1/admin/dashboard/sales?period=week
     * period: "week" | "month" | "year"
     * frontend ส่งมาเป็น A/B/C แล้ว SalesChart.js map ก่อนเรียก
     */
    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<SalesChartResponse>> getSalesChart(
            @RequestParam(defaultValue = "week") String period) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSalesChart(period)));
    }

    /** GET /api/v1/admin/dashboard/category-sales */
    @GetMapping("/category-sales")
    public ResponseEntity<ApiResponse<CategorySalesResponse>> getCategorySales() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getCategorySales()));
    }

    /** GET /api/v1/admin/dashboard/top-products */
    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getTopProducts()));
    }
}
