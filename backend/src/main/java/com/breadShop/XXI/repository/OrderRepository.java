package com.breadShop.XXI.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.breadShop.XXI.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    long countByUserId(Integer userId);

    List<Order> findByStatus(Order.OrderStatus status);
}