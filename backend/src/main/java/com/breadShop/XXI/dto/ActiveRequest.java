package com.breadShop.XXI.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH { "active": true/false } — บอกค่าที่ต้องการตรง ๆ (เรียกซ้ำได้ผลเหมือนเดิม ต่างจาก toggle) */
public record ActiveRequest(
    @NotNull(message = "กรุณาระบุ active")
    Boolean active
) {}
