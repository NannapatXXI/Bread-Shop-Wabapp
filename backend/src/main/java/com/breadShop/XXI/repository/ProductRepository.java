package com.breadShop.XXI.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import jakarta.persistence.LockModeType;

/**
 * เอาไว้ติดต่อกับฐานข้อมูลสำหรับ Product โดยใช้ Spring Data JPA
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    long countByCategory(ProductCategory category);
    long countByStockGreaterThan(int stock);
    long countByExpiryDateBefore(LocalDate date);

    // Pessimistic write lock — ป้องกัน race condition ตอนหักสต็อก
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
}
