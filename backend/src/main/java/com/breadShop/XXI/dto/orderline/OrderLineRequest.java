// dto/orderline/OrderLineRequest.java
package com.breadShop.XXI.dto.orderline;

import java.math.BigDecimal;

// ใช้สำหรับรับข้อมูลแต่ละรายการสินค้าในคำสั่งซื้อจากลูกค้า  | reviewed by peak
public class OrderLineRequest {

    private Integer productId;
    private Integer quantity;
    private BigDecimal discountAmount;

   
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
}