package com.breadShop.XXI.dto.storefront;

import java.util.List;

/** ค่าที่ admin ตั้งไว้ตามจริง — รวมสินค้าแนะนำที่หมดอยู่ด้วย (ลูกค้าไม่เห็นตัวที่หมด) */
public record AdminStorefrontResponse(String heroImageUrl, List<Long> featuredProductIds) {}
