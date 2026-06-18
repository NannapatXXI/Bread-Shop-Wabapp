// [Claude] DTO สำหรับ widget สินค้าขายดี
package com.breadShop.XXI.dto.dashboard;

import java.math.BigDecimal;

/**
 * DTO สำหรับ widget สินค้าขายดี
 */
public class TopProductResponse {

    private final Integer productId;
    private final String productName;
    private final long totalQty;
    private final BigDecimal totalRevenue;
    private final String imageUrl;

    public TopProductResponse(Integer productId, String productName,
                               long totalQty, BigDecimal totalRevenue, String imageUrl) {
        this.productId = productId;
        this.productName = productName;
        this.totalQty = totalQty;
        this.totalRevenue = totalRevenue;
        this.imageUrl = imageUrl;
    }

    public Integer getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getTotalQty() { return totalQty; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public String getImageUrl() { return imageUrl; }
}
