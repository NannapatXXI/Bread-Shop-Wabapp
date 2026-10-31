package com.breadShop.XXI.dto.dashboard;

import java.math.BigDecimal;

import com.breadShop.XXI.entity.ProductCategory;

/** ผลของ OrderLineRepository.sumRevenueByCategory — ยอดขายรวมของหมวดนี้ */
public record CategoryRevenue(ProductCategory category, BigDecimal revenue) {}
