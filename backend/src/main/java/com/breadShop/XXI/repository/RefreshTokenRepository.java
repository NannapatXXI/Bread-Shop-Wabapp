package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;

// Repository สำหรับจัดการข้อมูล RefreshToken โดยใช้ Spring Data JPA | reviewed by peak
public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    /** ลบ token ที่หมดอายุแล้ว */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** ลบ token ที่ถูก revoke แล้ว (logout) และหมดอายุแล้ว */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.revoked = true AND r.expiresAt < :now")
    int deleteRevokedAndExpired(@Param("now") LocalDateTime now);

    /** [Token Rotation] ลบ token เดิม 1 ตัวเมื่อจะ rotate */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
    void deleteByToken(@Param("token") String token);

    /** [One-Token-Per-User] ลบ token เก่าทั้งหมดของ user ก่อน login ใหม่ */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
