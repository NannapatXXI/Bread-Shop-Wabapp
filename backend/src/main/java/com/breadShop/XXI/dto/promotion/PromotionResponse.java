// dto/promotion/PromotionResponse.java
package com.breadShop.XXI.dto.promotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.breadShop.XXI.entity.Promotion.DiscountType;

/**
 * DTO สำหรับส่งข้อมูลโปรโมชั่นไปยัง client โดยไม่ต้องผ่าน entity ตรงๆ | reviewed by peak
 */
public class PromotionResponse {

    private final Integer id;
    private final String code;
    private final String name;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal minOrderAmount;
    private final BigDecimal maxDiscount;
    private final Integer usageLimit;
    private final Integer usedCount;
    private final LocalDateTime startedAt;
    private final LocalDateTime expiredAt;
    private final Boolean isActive;

    public PromotionResponse(Integer id, String code, String name,
                              DiscountType discountType, BigDecimal discountValue,
                              BigDecimal minOrderAmount, BigDecimal maxDiscount,
                              Integer usageLimit, Integer usedCount,
                              LocalDateTime startedAt, LocalDateTime expiredAt,
                              Boolean isActive) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscount = maxDiscount;
        this.usageLimit = usageLimit;
        this.usedCount = usedCount;
        this.startedAt = startedAt;
        this.expiredAt = expiredAt;
        this.isActive = isActive;
    }

    public Integer getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public Integer getUsageLimit() { return usageLimit; }
    public Integer getUsedCount() { return usedCount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public Boolean getIsActive() { return isActive; }
}