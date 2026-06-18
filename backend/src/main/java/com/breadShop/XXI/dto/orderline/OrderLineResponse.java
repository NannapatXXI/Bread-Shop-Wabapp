package com.breadShop.XXI.dto.orderline;

import java.math.BigDecimal;

// ใช้สำหรับส่งข้อมูลแต่ละรายการสินค้าในคำสั่งซื้อกลับไปยังลูกค้า | reviewed by peak
public class OrderLineResponse {

    private final Integer id;
    private final Integer productId;
    private final String productName;
    private final String productSku;
    private final String productImageUrl;
    private final BigDecimal unitPrice;
    private final Integer quantity;
    private final BigDecimal discountAmount;
    private final BigDecimal totalPrice;

    public OrderLineResponse(Integer id, Integer productId, String productName,
                              String productSku, String productImageUrl,
                              BigDecimal unitPrice, Integer quantity,
                              BigDecimal discountAmount, BigDecimal totalPrice) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.productImageUrl = productImageUrl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
    }

    public Integer getId() { return id; }
    public Integer getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductSku() { return productSku; }
    public String getProductImageUrl() { return productImageUrl; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}