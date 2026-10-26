package com.breadShop.XXI.dto.log;

public record LogSummaryResponse(
    long totalActivity,
    long totalAudit,
    long loginCount,
    long registerCount,
    long activeUsersToday
) {}
