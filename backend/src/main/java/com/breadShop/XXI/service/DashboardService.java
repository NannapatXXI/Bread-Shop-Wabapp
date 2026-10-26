package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.dto.dashboard.CategorySalesResponse;
import com.breadShop.XXI.dto.dashboard.CategorySalesResponse.CategoryData;
import com.breadShop.XXI.dto.dashboard.DashboardSummaryResponse;
import com.breadShop.XXI.dto.dashboard.SalesChartResponse;
import com.breadShop.XXI.dto.dashboard.SalesChartResponse.DataPoint;
import com.breadShop.XXI.dto.dashboard.TopProductResponse;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.repository.OrderLineRepository;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.UserRepository;

/**
 * ตัวเลขและกราฟของ admin dashboard
 * ยอดขายไม่นับ order ที่ยกเลิก/คืนเงิน — นับด้วย aggregate query ใน DB ไม่ดึงทุก order มาคิดใน memory
 */
@Service
public class DashboardService {

    /** ไม่นับเป็นยอดขาย */
    private static final List<OrderStatus> EXCLUDED = List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);
    private static final Locale THAI = Locale.of("th");
    private static final int TOP_PRODUCTS = 7;
    private static final Map<String, String> CATEGORY_TH = Map.of(
            "BREAD", "ขนมปัง", "CAKE", "เค้ก", "COOKIE", "คุกกี้", "DRINK", "เครื่องดื่ม");

    private final OrderRepository     orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final UserRepository      userRepository;

    public DashboardService(OrderRepository orderRepository, OrderLineRepository orderLineRepository,
                            UserRepository userRepository) {
        this.orderRepository     = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.userRepository      = userRepository;
    }

    /** การ์ดสรุป — "เดือนนี้" = 30 วันล่าสุด เทียบกับ 30 วันก่อนหน้า */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrow   = today.plusDays(1).atStartOfDay();
        LocalDateTime last30     = today.minusDays(30).atStartOfDay();
        LocalDateTime prev30     = today.minusDays(60).atStartOfDay();

        BigDecimal todayRevenue     = orderRepository.sumRevenueBetween(todayStart, tomorrow, EXCLUDED);
        BigDecimal thisMonthRevenue = orderRepository.sumRevenueBetween(last30, tomorrow, EXCLUDED);
        BigDecimal lastMonthRevenue = orderRepository.sumRevenueBetween(prev30, last30, EXCLUDED);
        long todayOrders     = orderRepository.countOrdersBetween(todayStart, tomorrow);
        long thisMonthOrders = orderRepository.countOrdersBetween(last30, tomorrow);
        long lastMonthOrders = orderRepository.countOrdersBetween(prev30, last30);

        return new DashboardSummaryResponse(
                todayRevenue,
                todayOrders,
                thisMonthRevenue,
                orderRepository.count(),
                userRepository.count(),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                changePercent(lastMonthRevenue, thisMonthRevenue),
                changePercent(lastMonthOrders, thisMonthOrders));
    }

    /**
     * กราฟยอดขายเทียบช่วงก่อนหน้า
     *   week  → 7 วันล่าสุด เทียบ 7 วันก่อนหน้า
     *   month → 4 สัปดาห์ของเดือนนี้ เทียบเดือนก่อน
     *   year  → 12 เดือนของปีนี้ เทียบปีก่อน
     */
    @Transactional(readOnly = true)
    public SalesChartResponse getSalesChart(String period) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = switch (period) {
            case "month" -> today.withDayOfMonth(1).minusMonths(1).atStartOfDay();
            case "year"  -> LocalDate.of(today.getYear() - 1, 1, 1).atStartOfDay();
            default      -> today.minusDays(13).atStartOfDay();
        };
        List<Order> orders = orderRepository.findByDateRangeAndStatusNotIn(from, today.plusDays(1).atStartOfDay(), EXCLUDED);

        return new SalesChartResponse(switch (period) {
            case "month" -> monthlyChart(orders);
            case "year"  -> yearlyChart(orders);
            default      -> weeklyChart(orders);
        });
    }

    /** ยอดขายแยกหมวด 30 วันล่าสุด */
    @Transactional(readOnly = true)
    public CategorySalesResponse getCategorySales() {
        LocalDateTime now = LocalDateTime.now();
        List<CategoryData> data = orderLineRepository.sumRevenueByCategory(now.minusDays(30), now, EXCLUDED).stream()
                .map(row -> {
                    String category = row[0] != null ? row[0].toString() : "OTHER";
                    BigDecimal revenue = row[1] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
                    return new CategoryData(CATEGORY_TH.getOrDefault(category, category), revenue);
                })
                .toList();
        return new CategorySalesResponse(data);
    }

    /** สินค้าขายดี 7 อันดับ 30 วันล่าสุด */
    @Transactional(readOnly = true)
    public List<TopProductResponse> getTopProducts() {
        LocalDateTime now = LocalDateTime.now();
        return orderLineRepository.getTopProductStats(now.minusDays(30), now, EXCLUDED).stream()
                .limit(TOP_PRODUCTS)
                .map(row -> new TopProductResponse(
                        ((Number) row[0]).intValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        row[3] instanceof BigDecimal bd ? bd : BigDecimal.ZERO,
                        (String) row[4]))
                .toList();
    }

    // ─── กราฟ ──────────────────────────────────────────────────────────

    private List<DataPoint> weeklyChart(List<Order> orders) {
        List<DataPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            points.add(new DataPoint(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, THAI),
                    revenue(orders, day, day.plusDays(1)),
                    revenue(orders, day.minusDays(7), day.minusDays(6))));
        }
        return points;
    }

    private List<DataPoint> monthlyChart(List<Order> orders) {
        List<DataPoint> points = new ArrayList<>();
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        for (int week = 0; week < 4; week++) {
            LocalDate start     = thisMonth.plusWeeks(week);
            LocalDate prevStart = lastMonth.plusWeeks(week);
            points.add(new DataPoint("Week " + (week + 1),
                    revenue(orders, start, start.plusWeeks(1)),
                    revenue(orders, prevStart, prevStart.plusWeeks(1))));
        }
        return points;
    }

    private List<DataPoint> yearlyChart(List<Order> orders) {
        List<DataPoint> points = new ArrayList<>();
        int year = LocalDate.now().getYear();
        for (Month month : Month.values()) {
            LocalDate start     = LocalDate.of(year, month, 1);
            LocalDate prevStart = LocalDate.of(year - 1, month, 1);
            points.add(new DataPoint(month.getDisplayName(TextStyle.SHORT, THAI),
                    revenue(orders, start, start.plusMonths(1)),
                    revenue(orders, prevStart, prevStart.plusMonths(1))));
        }
        return points;
    }

    /** ยอดรวมของ order ที่สร้างในช่วง [from, to) */
    private static BigDecimal revenue(List<Order> orders, LocalDate from, LocalDate to) {
        return orders.stream()
                .filter(o -> {
                    LocalDate d = o.getCreatedAt().toLocalDate();
                    return !d.isBefore(from) && d.isBefore(to);
                })
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** % เปลี่ยนแปลงจากช่วงก่อน — ช่วงก่อนเป็น 0 ตอบ 0 (หารด้วยศูนย์ไม่ได้) */
    private static double changePercent(BigDecimal prev, BigDecimal cur) {
        if (prev == null || prev.signum() == 0) {
            return 0.0;
        }
        return cur.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private static double changePercent(long prev, long cur) {
        return prev == 0 ? 0.0 : (double) (cur - prev) / prev * 100.0;
    }
}
