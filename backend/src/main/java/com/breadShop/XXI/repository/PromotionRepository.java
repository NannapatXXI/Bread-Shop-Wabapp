package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.breadShop.XXI.entity.Promotion;

// Repository สำหรับจัดการข้อมูล Promotion โดยใช้ Spring Data JPA | reviewed by peak
public interface PromotionRepository extends JpaRepository<Promotion, Integer> {

    Optional<Promotion> findByCode(String code);

    // หาโปรที่ active และยังไม่หมดอายุ
    Optional<Promotion> findByCodeAndIsActiveTrueAndExpiredAtAfter(
            String code, LocalDateTime now);
}