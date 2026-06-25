// dto/order/OrderRequest.java
package com.breadShop.XXI.dto.order;

import java.math.BigDecimal;
import java.util.List;

import com.breadShop.XXI.dto.orderline.OrderLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

// ใช้สำหรับรับข้อมูลการสร้างคำสั่งซื้อจากลูกค้า | reviewed by peak
public class OrderRequest {

    private Integer userId;

    @NotNull(message = "กรุณาเลือกที่อยู่จัดส่ง")
    private Integer addressId;
    private String promotionCode;
    private BigDecimal shippingFee;
    private String note;

    @NotEmpty(message = "กรุณาเพิ่มสินค้าในตะกร้า")
    @Valid
    private List<OrderLineRequest> items;

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