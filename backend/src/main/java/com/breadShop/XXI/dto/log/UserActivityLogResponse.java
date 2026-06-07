package com.breadShop.XXI.dto.log;

import java.time.LocalDateTime;

import com.breadShop.XXI.entity.UserActivityLog;

public record UserActivityLogResponse(
    Long          id,
    Integer       userId,
    String        username,
    String        action,
    String        ipAddress,
    String        details,
    String        status,
    LocalDateTime createdAt
) {
    public static UserActivityLogResponse from(UserActivityLog log) {
        return new UserActivityLogResponse(
            log.getId(),
            log.getUser() != null ? log.getUser().getId() : null,
            log.getUsername(),
            log.getAction(),
            log.getIpAddress(),
            log.getDetails(),
            log.getStatus(),
            log.getCreatedAt()
        );
    }
}
