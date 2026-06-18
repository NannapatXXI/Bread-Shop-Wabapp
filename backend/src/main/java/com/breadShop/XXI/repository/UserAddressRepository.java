package com.breadShop.XXI.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.breadShop.XXI.entity.UserAddress;

// Repository สำหรับจัดการข้อมูล UserAddress โดยใช้ Spring Data JPA | reviewed by peak
public interface UserAddressRepository extends JpaRepository<UserAddress, Integer> {

    List<UserAddress> findByUserId(Integer userId);

    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Integer userId);

    boolean existsByUserIdAndIsDefaultTrue(Integer userId);
}