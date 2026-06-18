// dto/order/OrderRequest.java
package com.breadShop.XXI.dto.order;

import java.math.BigDecimal;
import java.util.List;

import com.breadShop.XXI.dto.orderline.OrderLineRequest;

// ใช้สำหรับรับข้อมูลการสร้างคำสั่งซื้อจากลูกค้า | reviewed by peak
public class OrderRequest {

    private Integer userId;
    private Integer addressId;       // ลูกค้าเลือกที่อยู่ไหน
    private String promotionCode;    // โค้ดส่วนลด (ถ้ามี)
    private BigDecimal shippingFee;
    private String note;
    private List<OrderLineRequest> items;  // สินค้าที่สั่ง

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }

    public String getPromotionCode() { return promotionCode; }
    public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<OrderLineRequest> getItems() { return items; }
    public void setItems(List<OrderLineRequest> items) { this.items = items; }
}