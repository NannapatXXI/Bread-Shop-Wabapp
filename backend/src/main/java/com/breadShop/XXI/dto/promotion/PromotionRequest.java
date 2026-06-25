// dto/promotion/PromotionRequest.java
package com.breadShop.XXI.dto.promotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.breadShop.XXI.entity.Promotion.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * DTO สำหรับรับข้อมูลโปรโมชั่นใหม่จาก client เมื่อสร้างหรือแก้ไขโปรโมชั่น | reviewed by peak
 */
public class PromotionRequest {

    @NotBlank(message = "กรุณากรอกโค้ดโปรโมชั่น")
    private String code;

    @NotBlank(message = "กรุณากรอกชื่อโปรโมชั่น")
    private String name;

    @NotNull(message = "กรุณาเลือกประเภทส่วนลด")
    private DiscountType discountType;

    @NotNull(message = "กรุณากรอกมูลค่าส่วนลด")
    @DecimalMin(value = "0.01", message = "มูลค่าส่วนลดต้องมากกว่า 0")
    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscount;
    private Integer usageLimit;

    @NotNull(message = "กรุณาระบุวันเริ่มต้น")
    private LocalDateTime startedAt;

    @NotNull(message = "กรุณาระบุวันหมดอายุ")
    private LocalDateTime expiredAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }

    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
}