// dto/promotion/PromotionValidateResponse.java — ผลการตรวจโค้ด
package com.breadShop.XXI.dto.promotion;

import java.math.BigDecimal;

/**
 * DTO สำหรับส่งผลการตรวจสอบโค้ดโปรโมชั่นกลับไปยัง client | reviewed by peak
 */
public class PromotionValidateResponse {

    private final String code;
    private final String name;
    private final BigDecimal discountAmount;  // ส่วนลดที่ได้จริง

    public PromotionValidateResponse(String code, String name, BigDecimal discountAmount) {
        this.code = code;
        this.name = name;
        this.discountAmount = discountAmount;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
}