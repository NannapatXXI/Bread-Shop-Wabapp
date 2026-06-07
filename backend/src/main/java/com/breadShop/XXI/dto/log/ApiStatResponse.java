package com.breadShop.XXI.dto.log;

public record ApiStatResponse(
    String endpoint,
    String method,
    long   totalCalls,
    long   avgDurationMs,
    long   successCount,
    long   errorCount,
    double successRate,
    double errorRate
) {}
