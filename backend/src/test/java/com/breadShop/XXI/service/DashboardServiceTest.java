package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.breadShop.XXI.dto.dashboard.CategoryRevenue;
import com.breadShop.XXI.dto.dashboard.CategorySalesResponse.CategoryData;
import com.breadShop.XXI.dto.dashboard.DashboardSummaryResponse;
import com.breadShop.XXI.dto.dashboard.SalesChartResponse;
import com.breadShop.XXI.dto.dashboard.TopProductResponse;
import com.breadShop.XXI.dto.dashboard.TopProductStat;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.OrderLineRepository;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderLineRepository orderLineRepository;

    @Mock
    private UserRepository userRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(orderRepository, orderLineRepository, userRepository);
    }

    private Order orderWith(LocalDateTime createdAt, BigDecimal totalAmount) {
        Order order = new Order();
        order.setCreatedAt(createdAt);
        order.setTotalAmount(totalAmount);
        return order;
    }

    @Test
    void getSummary_shouldComputePercentChangesCorrectly() {
        when(orderRepository.sumRevenueBetween(any(), any(), anyList()))
                .thenReturn(BigDecimal.valueOf(100))   // todayRevenue
                .thenReturn(BigDecimal.valueOf(1000))  // thisMonthRev
                .thenReturn(BigDecimal.valueOf(500));  // lastMonthRev
        when(orderRepository.countOrdersBetween(any(), any()))
                .thenReturn(5L)   // todayOrders
                .thenReturn(50L)  // thisMonthOrders
                .thenReturn(25L); // lastMonthOrders
        when(orderRepository.count()).thenReturn(200L);
        when(userRepository.count()).thenReturn(80L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(3L);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertEquals(BigDecimal.valueOf(100), summary.getTodayRevenue());
        assertEquals(5L, summary.getTodayOrders());
        assertEquals(BigDecimal.valueOf(1000), summary.getMonthRevenue());
        assertEquals(200L, summary.getTotalOrders());
        assertEquals(80L, summary.getTotalCustomers());
        assertEquals(3L, summary.getCancelledOrders());
        assertEquals(100.0, summary.getMonthRevenueChangePercent());
        assertEquals(100.0, summary.getTotalOrdersChangePercent());
    }

    @Test
    void getSummary_whenLastMonthValuesAreZero_shouldReturnZeroChangePercent() {
        when(orderRepository.sumRevenueBetween(any(), any(), anyList()))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.valueOf(500))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any()))
                .thenReturn(0L)
                .thenReturn(10L)
                .thenReturn(0L);
        when(orderRepository.count()).thenReturn(10L);
        when(userRepository.count()).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(0L);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertEquals(0.0, summary.getMonthRevenueChangePercent());
        assertEquals(0.0, summary.getTotalOrdersChangePercent());
    }

    @Test
    void getSalesChart_withDefaultWeekPeriod_shouldReturn7Points() {
        LocalDateTime todayNoon = LocalDate.now().atTime(12, 0);
        when(orderRepository.findByDateRangeAndStatusNotIn(any(), any(), anyList()))
                .thenReturn(List.of(orderWith(todayNoon, BigDecimal.valueOf(50))));

        SalesChartResponse chart = dashboardService.getSalesChart("week");

        assertThat(chart.getData()).hasSize(7);
        assertEquals(BigDecimal.valueOf(50), chart.getData().get(6).getCurrent());
    }

    @Test
    void getSalesChart_withMonthPeriod_shouldReturn4Points() {
        when(orderRepository.findByDateRangeAndStatusNotIn(any(), any(), anyList()))
                .thenReturn(List.of());

        SalesChartResponse chart = dashboardService.getSalesChart("month");

        assertThat(chart.getData()).hasSize(4);
    }

    @Test
    void getSalesChart_withYearPeriod_shouldReturn12Points() {
        when(orderRepository.findByDateRangeAndStatusNotIn(any(), any(), anyList()))
                .thenReturn(List.of());

        SalesChartResponse chart = dashboardService.getSalesChart("year");

        assertThat(chart.getData()).hasSize(12);
    }

    @Test
    void getCategorySales_shouldMapEveryCategoryToThai_andKeepRevenue() {
        List<CategoryRevenue> rows = List.of(
                new CategoryRevenue(ProductCategory.BREAD, BigDecimal.valueOf(300)),
                new CategoryRevenue(ProductCategory.CAKE, BigDecimal.valueOf(50)),
                new CategoryRevenue(ProductCategory.COOKIE, BigDecimal.valueOf(20)),
                new CategoryRevenue(ProductCategory.DRINK, BigDecimal.valueOf(5)));
        when(orderLineRepository.sumRevenueByCategory(any(), any(), anyList())).thenReturn(rows);

        List<CategoryData> data = dashboardService.getCategorySales().getData();

        assertThat(data).extracting(CategoryData::getName).containsExactly("ขนมปัง", "เค้ก", "คุกกี้", "เครื่องดื่ม");
        assertEquals(BigDecimal.valueOf(300), data.get(0).getValue());
    }

    @Test
    void getTopProducts_shouldLimitToSevenAndMapFields() {
        List<TopProductStat> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(new TopProductStat((long) i, "Product " + i, (long) i, BigDecimal.valueOf(i * 10L), "img" + i + ".png"));
        }
        when(orderLineRepository.getTopProductStats(any(), any(), anyList())).thenReturn(rows);

        List<TopProductResponse> result = dashboardService.getTopProducts();

        assertThat(result).hasSize(7);
        assertEquals(1, result.get(0).getProductId());
        assertEquals("Product 1", result.get(0).getProductName());
        assertEquals("img1.png", result.get(0).getImageUrl());
        assertEquals(1L, result.get(0).getTotalQty());
        assertEquals(BigDecimal.valueOf(10), result.get(0).getTotalRevenue());
    }

    @Test
    void getSummary_whenLastMonthRevenueIsNull_shouldReturnZeroChangePercent() {
        when(orderRepository.sumRevenueBetween(any(), any(), anyList()))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.valueOf(500))
                .thenReturn(null);
        when(orderRepository.countOrdersBetween(any(), any()))
                .thenReturn(0L)
                .thenReturn(10L)
                .thenReturn(5L);
        when(orderRepository.count()).thenReturn(10L);
        when(userRepository.count()).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(0L);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertEquals(0.0, summary.getMonthRevenueChangePercent());
    }

}
