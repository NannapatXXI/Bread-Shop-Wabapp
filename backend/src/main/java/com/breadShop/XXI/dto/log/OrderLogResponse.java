package com.breadShop.XXI.dto.log;

import java.time.LocalDateTime;

import com.breadShop.XXI.entity.OrderLog;

public record OrderLogResponse(
    Long          id,
    Integer       orderId,
    String        orderNo,
    Integer       userId,
    String        oldStatus,
    String        newStatus,
    Integer       changedById,
    String        changedByName,
    String        trackingNo,
    String        note,
    LocalDateTime createdAt
) {
    public static OrderLogResponse from(OrderLog log) {
        return new OrderLogResponse(
            log.getId(),
            log.getOrder() != null ? log.getOrder().getId() : null,
            log.getOrderNo(),
            log.getUser() != null ? log.getUser().getId() : null,
            log.getOldStatus(),
            log.getNewStatus(),
            log.getChangedBy() != null ? log.getChangedBy().getId() : null,
            log.getChangedByName(),
            log.getTrackingNo(),
            log.getNote(),
            log.getCreatedAt()
        );
    }
}
