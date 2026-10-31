package com.breadShop.XXI.dto.dashboard;

import java.math.BigDecimal;

/** ผลของ OrderLineRepository.getTopProductStats — สินค้า 1 ตัวกับจำนวนและยอดที่ขายได้ */
public record TopProductStat(Long productId, String productName, Long totalQty, BigDecimal totalRevenue, String imageUrl) {}
