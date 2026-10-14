package com.breadShop.XXI.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.breadShop.XXI.dto.log.ApiStatResponse;
import com.breadShop.XXI.dto.log.ErrorTrendPoint;
import com.breadShop.XXI.dto.log.FailedRequestResponse;
import com.breadShop.XXI.entity.ApiRequestLog;
import com.breadShop.XXI.repository.ApiRequestLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiRequestLogServiceTest {

    @Mock
    private ApiRequestLogRepository repo;

    private ApiRequestLogService service;

    @BeforeEach
    void setUp() {
        service = new ApiRequestLogService(repo);
    }

    @Test
    void save_shouldPersistLog() {
        service.save("GET", "/api/products", "/api/products", 200, 15L, "user@x.com", "127.0.0.1");

        verify(repo).save(any(ApiRequestLog.class));
    }

    @Test
    void save_whenRepoThrows_shouldNotPropagateException() {
        doThrow(new RuntimeException("db down")).when(repo).save(any());

        assertDoesNotThrow(() -> service.save("GET", "/api/products", "/api/products", 200, 15L, "u", "ip"));
    }

    @Test
    void getApiStats_shouldComputeRatesCorrectly() {
        Object[] row = new Object[]{"/api/products", "GET", 10L, 50L, 8L, 2L};
        when(repo.getApiStats()).thenReturn(List.<Object[]>of(row));

        List<ApiStatResponse> stats = service.getApiStats();

        assertThat(stats).hasSize(1);
        ApiStatResponse stat = stats.get(0);
        assertEquals("/api/products", stat.endpoint());
        assertEquals(10L, stat.totalCalls());
        assertEquals(80.0, stat.successRate());
        assertEquals(20.0, stat.errorRate());
    }

    @Test
    void getApiStats_whenTotalIsZero_shouldReturnZeroRates() {
        Object[] row = new Object[]{"/api/empty", "GET", 0L, 0L, 0L, 0L};
        when(repo.getApiStats()).thenReturn(List.<Object[]>of(row));

        List<ApiStatResponse> stats = service.getApiStats();

        assertEquals(0.0, stats.get(0).successRate());
        assertEquals(0.0, stats.get(0).errorRate());
    }

    @Test
    void getFailedRequests_shouldReturnMappedPage() {
        ApiRequestLog failed = new ApiRequestLog("GET", "/api/x", "/api/x", 500, 10L, "u@x.com", "127.0.0.1");
        when(repo.findByStatusCodeGreaterThanEqualOrderByCreatedAtDesc(anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(failed)));

        Page<FailedRequestResponse> result = service.getFailedRequests(0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertEquals(500, result.getContent().get(0).statusCode());
    }

    @Test
    void getErrorBreakdown_shouldMapRows() {
        Object[] row = new Object[]{"404 Not Found", 5L};
        when(repo.getErrorBreakdown()).thenReturn(List.<Object[]>of(row));

        List<Map<String, Object>> result = service.getErrorBreakdown();

        assertEquals("404 Not Found", result.get(0).get("name"));
        assertEquals(5L, result.get(0).get("value"));
    }

    @Test
    void getErrorTrend_shouldMapRows() {
        Object[] row = new Object[]{"2024-01-01", 3L};
        when(repo.getErrorTrendSince(any(LocalDateTime.class))).thenReturn(List.<Object[]>of(row));

        List<ErrorTrendPoint> result = service.getErrorTrend(7);

        assertEquals("2024-01-01", result.get(0).date());
        assertEquals(3L, result.get(0).errors());
    }

    @Test
    void totalRequests_shouldReturnRepoCount() {
        when(repo.count()).thenReturn(100L);

        assertEquals(100L, service.totalRequests());
    }

    @Test
    void totalErrors_shouldReturnRepoCount() {
        when(repo.countByStatusCodeGreaterThanEqual(400)).thenReturn(7L);

        assertEquals(7L, service.totalErrors());
    }
}
