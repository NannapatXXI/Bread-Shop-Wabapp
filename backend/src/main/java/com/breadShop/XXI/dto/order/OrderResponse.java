// dto/order/OrderResponse.java
package com.breadShop.XXI.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.Order.OrderStatus;

// ใช้สำหรับส่งข้อมูลคำสั่งซื้อกลับไปยัง client หลังจากสร้างหรือเมื่อดึงข้อมูลคำสั่งซื้อ | reviewed by peak
public class OrderResponse {

    private final Integer id;
    private final String orderNo;
    private final Integer userId;
    private final String shippingName;
    private final String shippingPhone;
    private final String shippingAddress;
    private final String shippingProvince;
    private final String shippingDistrict;
    private final String shippingSubdistrict;
    private final String shippingPostcode;
    private final BigDecimal subtotal;
    private final BigDecimal discountAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal totalAmount;
    private final String promotionCode;
    private final OrderStatus status;
    private final String trackingNo;
    private final String note;
    private final List<OrderLineResponse> orderLines;
    private final LocalDateTime createdAt;


    public OrderResponse(Integer id, String orderNo, Integer userId,
                          String shippingName, String shippingPhone,
                          String shippingAddress, String shippingProvince,
                          String shippingDistrict, String shippingSubdistrict,
                          String shippingPostcode, BigDecimal subtotal,
                          BigDecimal discountAmount, BigDecimal shippingFee,
                          BigDecimal totalAmount, String promotionCode,
                          OrderStatus status, String trackingNo, String note,
                          List<OrderLineResponse> orderLines,
                          LocalDateTime createdAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.userId = userId;
        this.shippingName = shippingName;
        this.shippingPhone = shippingPhone;
        this.shippingAddress = shippingAddress;
        this.shippingProvince = shippingProvince;
        this.shippingDistrict = shippingDistrict;
        this.shippingSubdistrict = shippingSubdistrict;
        this.shippingPostcode = shippingPostcode;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.shippingFee = shippingFee;
        this.totalAmount = totalAmount;
        this.promotionCode = promotionCode;
        this.status = status;
        this.trackingNo = trackingNo;
        this.note = note;
        this.orderLines = orderLines;
        this.createdAt = createdAt;
    }

    public Integer getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Integer getUserId() { return userId; }
    public String getShippingName() { return shippingName; }
    public String getShippingPhone() { return shippingPhone; }
    public String getShippingAddress() { return shippingAddress; }
    public String getShippingProvince() { return shippingProvince; }
    public String getShippingDistrict() { return shippingDistrict; }
    public String getShippingSubdistrict() { return shippingSubdistrict; }
    public String getShippingPostcode() { return shippingPostcode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getPromotionCode() { return promotionCode; }
    public OrderStatus getStatus() { return status; }
    public String getTrackingNo() { return trackingNo; }
    public String getNote() { return note; }
    public List<OrderLineResponse> getOrderLines() { return orderLines; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}