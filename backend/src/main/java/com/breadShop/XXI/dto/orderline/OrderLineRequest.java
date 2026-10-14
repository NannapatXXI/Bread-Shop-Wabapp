// dto/orderline/OrderLineRequest.java
package com.breadShop.XXI.dto.orderline;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderLineRequest {

    @NotNull(message = "กรุณาระบุสินค้า")
    private Integer productId;

    @NotNull(message = "กรุณาระบุจำนวน")
    @Min(value = 1, message = "จำนวนสินค้าต้องมากกว่า 0")
    private Integer quantity;
    // ไม่มี discountAmount / ราคา — ราคาและส่วนลดคิดฝั่ง server เท่านั้น (field อื่นที่ client ส่งมาถูกเมิน)

   
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}