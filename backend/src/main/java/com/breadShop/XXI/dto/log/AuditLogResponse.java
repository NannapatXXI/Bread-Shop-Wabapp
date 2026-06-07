package com.breadShop.XXI.dto.log;

import java.time.LocalDateTime;

import com.breadShop.XXI.entity.AuditLog;

public record AuditLogResponse(
    Long          id,
    Integer       actorId,
    String        actorName,
    String        actorRole,
    String        action,
    String        entityType,
    String        entityId,
    String        oldValue,
    String        newValue,
    String        details,
    LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
            log.getId(),
            log.getActor() != null ? log.getActor().getId() : null,
            log.getActorName(),
            log.getActorRole(),
            log.getAction(),
            log.getEntityType(),
            log.getEntityId(),
            log.getOldValue(),
            log.getNewValue(),
            log.getDetails(),
            log.getCreatedAt()
        );
    }
}
