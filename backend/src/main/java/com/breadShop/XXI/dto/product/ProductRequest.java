package com.breadShop.XXI.dto.product;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.web.multipart.MultipartFile;

import com.breadShop.XXI.entity.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


//  DTO สำหรับรับข้อมูลสินค้าใหม่จาก client เมื่อสร้างหรือแก้ไขสินค้า | reviewed by peak
  
public class ProductRequest {

    @NotBlank(message = "กรุณากรอกชื่อสินค้า")
    private String name;

    @NotNull(message = "กรุณากรอกราคา")
    @DecimalMin(value = "0.01", message = "ราคาต้องมากกว่า 0")
    private BigDecimal price;

    @NotNull(message = "กรุณากรอกจำนวนสต็อก")
    @Min(value = 0, message = "จำนวนสต็อกต้องไม่ติดลบ")
    private Integer stock;

    private String description;
    private ProductCategory category;
    private LocalDate expiryDate;
    private MultipartFile image;

    public ProductRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }
}