package com.breadShop.XXI.dto.log;

import java.time.LocalDateTime;

import com.breadShop.XXI.entity.ApiRequestLog;

public record FailedRequestResponse(
    Long          id,
    String        method,
    String        endpoint,
    int           statusCode,
    long          durationMs,
    String        userEmail,
    LocalDateTime createdAt
) {
    public static FailedRequestResponse from(ApiRequestLog log) {
        return new FailedRequestResponse(
            log.getId(), log.getMethod(), log.getEndpoint(),
            log.getStatusCode(), log.getDurationMs(),
            log.getUserEmail(), log.getCreatedAt()
        );
    }
}
