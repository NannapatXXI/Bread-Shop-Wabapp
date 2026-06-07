package com.breadShop.XXI.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.breadShop.XXI.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Integer userId);

    long countByUserIdAndIsReadFalse(Integer userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId")
    int markAllReadByUserId(Integer userId);
}
