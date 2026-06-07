package com.breadShop.XXI.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.ApiStatResponse;
import com.breadShop.XXI.dto.log.ErrorTrendPoint;
import com.breadShop.XXI.dto.log.FailedRequestResponse;
import com.breadShop.XXI.entity.ApiRequestLog;
import com.breadShop.XXI.repository.ApiRequestLogRepository;

@Service
public class ApiRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogService.class);

    private final ApiRequestLogRepository repo;

    public ApiRequestLogService(ApiRequestLogRepository repo) {
        this.repo = repo;
    }

    public void save(String method, String endpoint, String uri,
                     int statusCode, long durationMs, String userEmail, String ipAddress) {
        try {
            repo.save(new ApiRequestLog(method, endpoint, uri, statusCode, durationMs, userEmail, ipAddress));
        } catch (Exception e) {
            log.error("Failed to save ApiRequestLog", e);
        }
    }

    public List<ApiStatResponse> getApiStats() {
        return repo.getApiStats().stream().map(row -> {
            long total   = ((Number) row[2]).longValue();
            long avgMs   = ((Number) row[3]).longValue();
            long success = ((Number) row[4]).longValue();
            long errors  = ((Number) row[5]).longValue();
            double successRate = total > 0 ? Math.round(success * 1000.0 / total) / 10.0 : 0;
            double errorRate   = total > 0 ? Math.round(errors  * 1000.0 / total) / 10.0 : 0;
            return new ApiStatResponse(
                (String) row[0], (String) row[1],
                total, avgMs, success, errors, successRate, errorRate
            );
        }).collect(Collectors.toList());
    }

    public Page<FailedRequestResponse> getFailedRequests(int page, int size) {
        return repo.findByStatusCodeGreaterThanEqualOrderByCreatedAtDesc(400, PageRequest.of(page, size))
                   .map(FailedRequestResponse::from);
    }

    public List<Map<String, Object>> getErrorBreakdown() {
        return repo.getErrorBreakdown().stream().map(row ->
            Map.<String, Object>of("name", row[0], "value", ((Number) row[1]).longValue())
        ).collect(Collectors.toList());
    }

    public List<ErrorTrendPoint> getErrorTrend(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return repo.getErrorTrendSince(since).stream().map(row ->
            new ErrorTrendPoint(row[0].toString(), ((Number) row[1]).longValue())
        ).collect(Collectors.toList());
    }

    public long totalRequests() {
        return repo.count();
    }

    public long totalErrors() {
        return repo.countByStatusCodeGreaterThanEqual(400);
    }
}
