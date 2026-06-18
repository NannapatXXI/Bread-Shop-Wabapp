// [Claude] DTO สรุปตัวเลข summary cards บน dashboard
package com.breadShop.XXI.dto.dashboard;

import java.math.BigDecimal;

/**
 * DTO สรุปตัวเลข summary cards บน dashboard
 */
public class DashboardSummaryResponse {

    private final BigDecimal todayRevenue;
    private final long todayOrders;
    private final BigDecimal monthRevenue;
    private final long totalOrders;
    private final long totalCustomers;
    private final long cancelledOrders;
    private final double monthRevenueChangePercent;
    private final double totalOrdersChangePercent;

    public DashboardSummaryResponse(BigDecimal todayRevenue, long todayOrders,
                                     BigDecimal monthRevenue, long totalOrders,
                                     long totalCustomers, long cancelledOrders,
                                     double monthRevenueChangePercent,
                                     double totalOrdersChangePercent) {
        this.todayRevenue = todayRevenue;
        this.todayOrders = todayOrders;
        this.monthRevenue = monthRevenue;
        this.totalOrders = totalOrders;
        this.totalCustomers = totalCustomers;
        this.cancelledOrders = cancelledOrders;
        this.monthRevenueChangePercent = monthRevenueChangePercent;
        this.totalOrdersChangePercent = totalOrdersChangePercent;
    }

    public BigDecimal getTodayRevenue() { return todayRevenue; }
    public long getTodayOrders() { return todayOrders; }
    public BigDecimal getMonthRevenue() { return monthRevenue; }
    public long getTotalOrders() { return totalOrders; }
    public long getTotalCustomers() { return totalCustomers; }
    public long getCancelledOrders() { return cancelledOrders; }
    public double getMonthRevenueChangePercent() { return monthRevenueChangePercent; }
    public double getTotalOrdersChangePercent() { return totalOrdersChangePercent; }
}
