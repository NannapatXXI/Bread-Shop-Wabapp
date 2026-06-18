package com.breadShop.XXI.dto.product;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.breadShop.XXI.entity.ProductCategory;

/**
 * DTO สำหรับส่งข้อมูลสินค้าไปยัง client โดยไม่ต้องผ่าน entity ตรงๆ | reviewed by peak
 */
public class ProductResponse {


    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final Integer stock;
    private final String description;
    private final String imageUrl;
    private final ProductCategory category;   // หรือ type จริงของคุณ
    private final LocalDate expiryDate;
    
    public ProductResponse(Long id, String name, BigDecimal price,
                           Integer stock, String description,
                           String imageUrl, ProductCategory category,
                           LocalDate expiryDate) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.expiryDate = expiryDate;
    }

    // ===== Getter =====

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public ProductCategory getCategory() { return category; }
    public LocalDate getExpiryDate() { return expiryDate; }
    
}
