package com.breadShop.XXI.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;           // ✅ เปลี่ยนจาก Double

    @Column(nullable = false)
    private Integer stock;

    @Column(length = 1000)
    private String description;

    @Column(nullable = true)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)           // ✅ เพิ่ม @Column
    private ProductCategory category;

    @Column(nullable = true)
    private LocalDate expiryDate;

    /** สินค้าแนะนำบนหน้าร้าน — null = ไม่แนะนำ, เลขน้อยแสดงก่อน (admin ตั้งที่หน้าจัดการหน้าร้าน) */
    @Column(name = "featured_rank")
    private Integer featuredRank;

    /** false = ปิดการขาย — ลูกค้าไม่เห็นและสั่งไม่ได้ แต่ข้อมูลยังอยู่ (order เก่าอ้างถึงได้ / เปิดขายใหม่ได้) */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp                  // ✅ เพิ่มใหม่
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp                    // ✅ เพิ่มใหม่
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public Product() {}

    public Product(String name, BigDecimal price, Integer stock,
                   String description, String imageUrl,
                   ProductCategory category, LocalDate expiryDate) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.expiryDate = expiryDate;
    }

    // Getters & Setters
    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getFeaturedRank() { return featuredRank; }
    public void setFeaturedRank(Integer featuredRank) { this.featuredRank = featuredRank; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}