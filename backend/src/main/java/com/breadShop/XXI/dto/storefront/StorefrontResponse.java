package com.breadShop.XXI.dto.storefront;

import java.util.List;

import com.breadShop.XXI.dto.product.ProductResponse;

/** หน้าร้านที่ลูกค้าเห็น — heroImageUrl null = ยังไม่ได้ตั้ง (หน้าเว็บใช้รูปสินค้าแทน) */
public record StorefrontResponse(String heroImageUrl, List<ProductResponse> featured) {}
