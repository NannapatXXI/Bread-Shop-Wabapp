package com.breadShop.XXI.dto.storefront;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** สินค้าแนะนำเรียงตามลำดับที่จะแสดง — ส่ง [] = ยกเลิกทั้งหมด (กลับไปใช้สินค้าขายดี) */
public record FeaturedRequest(
    @NotNull(message = "กรุณาระบุรายการสินค้า")
    @Size(max = 8, message = "เลือกสินค้าแนะนำได้สูงสุด 8 รายการ")
    List<@NotNull Long> productIds
) {}
