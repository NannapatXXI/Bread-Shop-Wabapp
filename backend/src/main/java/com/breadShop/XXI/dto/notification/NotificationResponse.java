package com.breadShop.XXI.dto.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
    Integer id,
    Integer orderId,
    String orderNo,
    String message,
    String newStatus,
    boolean isRead,
    LocalDateTime createdAt
) {}
