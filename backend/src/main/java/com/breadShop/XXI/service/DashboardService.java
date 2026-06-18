// [Claude] Service รวบรวมข้อมูลสำหรับ admin dashboard ทั้งหมด
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
import java.util.stream.Collectors;

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

// Service รวบรวมข้อมูลสำหรับแสดงใน admin dashboard ทั้งหมดโดยใช้ aggregate queries แทนการดึงข้อมูลทั้งหมดมาแล้วคำนวณใน memory เพื่อประสิทธิภาพที่ดีกว่า reviewed by peak
@Service
public class DashboardService {

    private static final List<OrderStatus> EXCLUDED =
            List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED); // list ที่เก็บ status ที่ไม่เอามาคิดยอดขาย เช่น ยกเลิกและคืนเงิน

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final UserRepository userRepository;

    public DashboardService(OrderRepository orderRepository,
                             OrderLineRepository orderLineRepository,
                             UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.userRepository = userRepository;
    }

    // [Claude] คำนวณตัวเลข summary cards ด้วย aggregate queries แทน findAll()
    /**
     * ดึงข้อมูลสรุปสำหรับแสดงใน dashboard summary cards เช่น ยอดขายวันนี้, จำนวนออเดอร์วันนี้, ยอดขายเดือนนี้, จำนวนออเดอร์ทั้งหมด, จำนวนลูกค้าทั้งหมด และจำนวนออเดอร์ที่ถูกยกเลิก 
     * รวมถึงคำนวณเปอร์เซ็นต์การเปลี่ยนแปลงของยอดขายและจำนวนออเดอร์เมื่อเทียบกับเดือนก่อนหน้า 
     * @return ข้อมูลสรุปโดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        LocalDate last30Start = today.minusDays(30);
        LocalDate prev30Start = today.minusDays(60);

        LocalDateTime todayStart    = today.atStartOfDay();
        LocalDateTime todayEnd      = today.plusDays(1).atStartOfDay();
        LocalDateTime last30StartDt = last30Start.atStartOfDay();
        LocalDateTime prev30StartDt = prev30Start.atStartOfDay();

        BigDecimal todayRevenue    = orderRepository.sumRevenueBetween(todayStart, todayEnd, EXCLUDED);
        long       todayOrders     = orderRepository.countOrdersBetween(todayStart, todayEnd);
        BigDecimal thisMonthRev    = orderRepository.sumRevenueBetween(last30StartDt, todayEnd, EXCLUDED);
        BigDecimal lastMonthRev    = orderRepository.sumRevenueBetween(prev30StartDt, last30StartDt, EXCLUDED);
        long       thisMonthOrders = orderRepository.countOrdersBetween(last30StartDt, todayEnd);
        long       lastMonthOrders = orderRepository.countOrdersBetween(prev30StartDt, last30StartDt);
        long       totalOrders     = orderRepository.count();
        long       totalCustomers  = userRepository.count();
        long       cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED); //เอาแค่ cancelled ไม่เอา refunded เพราะ refunded อาจจะเกิดจากการคืนเงินหลังจากที่ order ถูกยกเลิกแล้ว

        double revenueChange = calcChangePercent(lastMonthRev, thisMonthRev);
        double ordersChange  = calcChangePercent(lastMonthOrders, thisMonthOrders);

        return new DashboardSummaryResponse(
                todayRevenue, todayOrders,
                thisMonthRev, totalOrders,
                totalCustomers, cancelledOrders,
                revenueChange, ordersChange
        );
    }

    // [Claude] ข้อมูล SalesChart แบ่งตาม period: week / month / year
    /**
     * ดึงข้อมูลยอดขายสำหรับแสดงในกราฟ SalesChart โดยสามารถเลือกช่วงเวลาได้เป็นรายสัปดาห์ (week), รายเดือน (month) หรือรายปี (year) โดยจะคำนวณยอดขายปัจจุบันและยอดขายของช่วงเวลาเดียวกันในปีก่อนหน้า 
     * @param period ช่วงเวลาที่ต้องการดูยอดขาย สามารถเป็น "week", "month" หรือ "year" หากไม่ระบุหรือระบุค่าอื่น จะถือว่าเป็น "week"
     * @return ข้อมูลยอดขายสำหรับกราฟ SalesChart โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ
     */
    @Transactional(readOnly = true)
    public SalesChartResponse getSalesChart(String period) {
        List<Order> allOrders = orderRepository.findAll()
                .stream()
                .filter(o -> !isExcluded(o.getStatus()))
                .collect(Collectors.toList());

        List<DataPoint> points = switch (period) {
            case "month" -> buildMonthlyChart(allOrders);
            case "year"  -> buildYearlyChart(allOrders);
            default      -> buildWeeklyChart(allOrders);
        };

        return new SalesChartResponse(points);
    }

    // [Claude] ยอดขายแบ่งตาม category 30 วันย้อนหลัง — ใช้ aggregate query แทน findAll()
    /**
     * ดึงข้อมูลยอดขายรวมแยกตามหมวดหมู่สินค้าในช่วง 30 วันย้อนหลัง โดยจะไม่รวม order ที่มีสถานะอยู่ในรายการ excluded และถ้าไม่มีหมวดหมู่ใดๆ จะจัดกลุ่มเป็น "OTHER" และแปลงชื่อหมวดหมู่เป็นภาษาไทยสำหรับหมวดหมู่ที่กำหนดไว้
     * @return ข้อมูลยอดขายรวมแยกตามหมวดหมู่สินค้าในรูปแบบ  CategorySalesResponse ซึ่งมีรายการข้อมูลแต่ละหมวดหมู่ที่ประกอบด้วยชื่อหมวดหมู่ 
     */
    @Transactional(readOnly = true)
    public CategorySalesResponse getCategorySales() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end   = LocalDateTime.now();

        List<Object[]> rows = orderLineRepository.sumRevenueByCategory(start, end, EXCLUDED);

        Map<String, String> thaiName = Map.of(
                "BREAD",  "ขนมปัง",
                "CAKE",   "เค้ก",
                "COOKIE", "คุกกี้",
                "DRINK",  "เครื่องดื่ม"
        );

        List<CategoryData> result = rows.stream()
                .map(row -> {
                    String catName = row[0] != null ? row[0].toString() : "OTHER";
                    BigDecimal revenue = row[1] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
                    return new CategoryData(thaiName.getOrDefault(catName, catName), revenue);
                })
                .collect(Collectors.toList());

        return new CategorySalesResponse(result);
    }

    // [Claude] top 7 สินค้าขายดีที่สุดใน 30 วัน — ใช้ aggregate query แทน findAll()
    /**
     * ดึงข้อมูลสถิติยอดขายของสินค้าที่ขายดีที่สุด (top products) ในช่วง 30 วันย้อนหลัง โดยจะไม่รวม order ที่มีสถานะอยู่ในรายการ excluded และจัดเรียงตามจำนวนสินค้าที่ขายได้ (quantity) จากมากไปน้อย
     *  โดยข้อมูลที่ได้จะมี productId, productName, sumQty, sumRevenue และ imageUrl
     * @return ข้อมูลสถิติยอดขายของสินค้าที่ขายดีที่สุดในรูปแบบ List<TopProductResponse> 
     */
    @Transactional(readOnly = true)
    public List<TopProductResponse> getTopProducts() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end   = LocalDateTime.now();

        List<Object[]> rows = orderLineRepository.getTopProductStats(start, end, EXCLUDED);

        return rows.stream()
                .limit(7)
                .map(row -> new TopProductResponse(
                        ((Number) row[0]).intValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        row[3] instanceof BigDecimal bd ? bd : BigDecimal.ZERO,
                        (String) row[4]
                ))
                .collect(Collectors.toList());
    }

    // ─── helpers ───────────────────────────────────────────────────────

    /**
     * คำนวณยอดขายรวมในแต่ละวันของสัปดาห์ปัจจุบันและสัปดาห์เดียวกันของปีก่อนหน้า โดยรับรายการ order ทั้งหมดและกรองข้อมูลตามวันที่
     *  จากนั้นสร้างจุดข้อมูลสำหรับกราฟโดยมี label เป็นชื่อวันในสัปดาห์ (เช่น "Mon", "Tue") และค่าปัจจุบันและค่าก่อนหน้า
     * @param orders รายการ order ทั้งหมดที่ต้องการคำนวณยอดขาย โดยจะกรองข้อมูลตามวันที่ภายในฟังก์ชันนี้
     * @return รายการจุดข้อมูลสำหรับกราฟยอดขายรายสัปดาห์ โดยแต่ละจุดข้อมูลมี label เป็นชื่อวันในสัปดาห์ และค่าปัจจุบันและค่าก่อนหน้า
     */
    private List<DataPoint> buildWeeklyChart(List<Order> orders) {
        List<DataPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDate prevDay = day.minusDays(7);
            BigDecimal cur = sumOnDay(orders, day);
            BigDecimal prev = sumOnDay(orders, prevDay);
            String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("th"));
            points.add(new DataPoint(label, cur, prev));
        }
        return points;
    }

    /**
     * คำนวณยอดขายรวมในแต่ละเดือนของปีปัจจุบันและปีเดียวกันของปีก่อนหน้า โดยรับรายการ order ทั้งหมดและกรองข้อมูลตามเดือนและปี
     * @param orders รายการ order ทั้งหมดที่ต้องการคำนวณยอดขาย โดยจะกรองข้อมูลตามเดือนและปีภายในฟังก์ชันนี้
     * @return รายการจุดข้อมูลสำหรับกราฟยอดขายรายเดือน โดยแต่ละจุดข้อมูลมี label เป็นชื่อเดือน (เช่น "Jan", "Feb") และค่าปัจจุบันและค่าก่อนหน้า
     */
    private List<DataPoint> buildYearlyChart(List<Order> orders) {
        List<DataPoint> points = new ArrayList<>();
        int thisYear = LocalDate.now().getYear();
        for (Month m : Month.values()) {
            LocalDate start = LocalDate.of(thisYear, m, 1);
            LocalDate end = start.plusMonths(1);
            LocalDate prevStart = LocalDate.of(thisYear - 1, m, 1);
            LocalDate prevEnd = prevStart.plusMonths(1);
            BigDecimal cur = sumRevenue(orders, start, end);
            BigDecimal prev = sumRevenue(orders, prevStart, prevEnd);
            String label = m.getDisplayName(TextStyle.SHORT, new Locale("th"));
            points.add(new DataPoint(label, cur, prev));
        }
        return points;
    }

    /**
     * คำนวณยอดขายรวมในแต่ละสัปดาห์ของเดือนปัจจุบันและเดือนเดียวกันของปีก่อนหน้า โดยรับรายการ order ทั้งหมดและกรองข้อมูลตามสัปดาห์ภายในเดือน
     * @param orders รายการ order ทั้งหมดที่ต้องการคำนวณยอดขาย โดยจะกรองข้อมูลตามสัปดาห์ภายในเดือนภายในฟังก์ชันนี้
     * @return รายการจุดข้อมูลสำหรับกราฟยอดขายรายสัปดาห์ภายในเดือน โดยแต่ละจุดข้อมูลมี label เป็นชื่อสัปดาห์ (เช่น "Week 1", "Week 2") และค่าปัจจุบันและค่าก่อนหน้า
     */
    private List<DataPoint> buildMonthlyChart(List<Order> orders) {
        List<DataPoint> points = new ArrayList<>();
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate firstOfLastMonth = firstOfMonth.minusMonths(1);
        for (int w = 0; w < 4; w++) {
            LocalDate wStart = firstOfMonth.plusWeeks(w);
            LocalDate wEnd = wStart.plusWeeks(1);
            LocalDate prevStart = firstOfLastMonth.plusWeeks(w);
            LocalDate prevEnd = prevStart.plusWeeks(1);
            BigDecimal cur = sumRevenue(orders, wStart, wEnd);
            BigDecimal prev = sumRevenue(orders, prevStart, prevEnd);
            points.add(new DataPoint("Week " + (w + 1), cur, prev));
        }
        return points;
    }

    /**
     * คำนวณยอดขายรวมในวันเดียวกันของสัปดาห์ปัจจุบันและสัปดาห์เดียวกันของปีก่อนหน้า โดยรับรายการ order ทั้งหมดและกรองข้อมูลตามวันที่
     * @param orders รายการ order ทั้งหมดที่ต้องการคำนวณยอดขาย โดยจะกรองข้อมูลตามวันที่ภายในฟังก์ชันนี้
     * @param day วันที่ที่ต้องการคำนวณยอดขาย
     * @return ยอดขายรวมในวันนั้น ๆ โดยไม่รวม order ที่มีสถานะอยู่ในรายการ excluded
     */
    private BigDecimal sumOnDay(List<Order> orders, LocalDate day) {
        return orders.stream()
                .filter(o -> o.getCreatedAt().toLocalDate().equals(day))
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * คำนวณยอดขายรวมในช่วงวันที่ที่กำหนด โดยรับรายการ order ทั้งหมดและกรองข้อมูลตามช่วงวันที่
     * @param orders รายการ order ทั้งหมดที่ต้องการคำนวณยอดขาย โดยจะกรองข้อมูลตามช่วงวันที่ภายในฟังก์ชันนี้
     * @param from วันที่เริ่มต้นของช่วงเวลาที่ต้องการคำนวณยอดขาย (รวมวันเริ่มต้น)
     * @param to วันที่สิ้นสุดของช่วงเวลาที่ต้องการคำนวณยอดขาย (ไม่รวมวันสิ้นสุด)
     * @return ยอดขายรวมในช่วงวันที่ที่กำหนด โดยไม่รวม order ที่มีสถานะอยู่ในรายการ excluded
     */
    private BigDecimal sumRevenue(List<Order> orders, LocalDate from, LocalDate to) {
        return orders.stream()
                .filter(o -> {
                    LocalDate d = o.getCreatedAt().toLocalDate();
                    return !d.isBefore(from) && d.isBefore(to);
                })
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    /**
     * คำนวณเปอร์เซ็นต์การเปลี่ยนแปลงระหว่างค่าก่อนหน้าและค่าปัจจุบัน โดยใช้สูตร (cur - prev) / prev * 100 และจัดการกรณีที่ prev เป็น 0 หรือ null
     * @param prev ค่าก่อนหน้า (previous value) ที่ใช้ในการคำนวณเปอร์เซ็นต์การเปลี่ยนแปลง
     * @param cur ค่าปัจจุบัน (current value) ที่ใช้ในการคำนวณเปอร์เซ็นต์การเปลี่ยนแปลง
     * @return เปอร์เซ็นต์การเปลี่ยนแปลงระหว่างค่าก่อนหน้าและค่าปัจจุบัน หาก prev เป็น 0 หรือ null จะคืนค่าเป็น 0.0
     */
    private double calcChangePercent(BigDecimal prev, BigDecimal cur) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return cur.subtract(prev)
                .divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * คำนวณเปอร์เซ็นต์การเปลี่ยนแปลงระหว่างค่าก่อนหน้าและค่าปัจจุบัน โดยใช้สูตร (cur - prev) / prev * 100 และจัดการกรณีที่ prev เป็น 0
     * @param prev ค่าก่อนหน้า (previous value) ที่ใช้ในการคำนวณเปอร์เซ็นต์การเปลี่ยนแปลง
     * @param cur ค่าปัจจุบัน (current value) ที่ใช้ในการคำนวณเปอร์เซ็นต์การเปลี่ยนแปลง
     * @return เปอร์เซ็นต์การเปลี่ยนแปลงระหว่างค่าก่อนหน้าและค่าปัจจุบัน หาก prev เป็น 0 จะคืนค่าเป็น 0.0 เพื่อหลีกเลี่ยงการหารด้วยศูนย์
     */
    private double calcChangePercent(long prev, long cur) {
        if (prev == 0) return 0.0;
        return ((double)(cur - prev) / prev) * 100.0;
    }

    /**
     * ตรวจสอบว่าสถานะของ order อยู่ในรายการ excluded หรือไม่ โดยจะไม่รวม order ที่มีสถานะเป็น CANCELLED หรือ REFUNDED ในการคำนวณยอดขายและสถิติอื่นๆ
     * @param status สถานะของ order ที่ต้องการตรวจสอบ
     * @return true หากสถานะของ order อยู่ในรายการ excluded (CANCELLED หรือ REFUNDED) และ false หากไม่อยู่ในรายการ excluded
     */
    private boolean isExcluded(OrderStatus status) {
        return status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED;
    }
}
