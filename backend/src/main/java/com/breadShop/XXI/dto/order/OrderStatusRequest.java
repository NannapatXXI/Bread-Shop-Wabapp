package com.breadShop.XXI.dto.order;

import com.breadShop.XXI.entity.Order.OrderStatus;

import jakarta.validation.constraints.NotNull;

/** admin เปลี่ยนสถานะ order — trackingNo ใส่ตอนส่งของ (ไม่บังคับ) */
public record OrderStatusRequest(
    @NotNull(message = "กรุณาระบุสถานะ")
    OrderStatus status,
    String trackingNo
) {}
