package com.breadShop.XXI.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.OrderLogResponse;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLog;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.OrderLogRepository;

@Service
public class OrderLogService {

    private static final Logger log = LoggerFactory.getLogger(OrderLogService.class);

    private final OrderLogRepository repo;

    public OrderLogService(OrderLogRepository repo) {
        this.repo = repo;
    }

    public void log(Order order, String oldStatus, String newStatus,
                    User changedBy, String trackingNo, String note) {
        try {
            String changedByName = changedBy != null ? changedBy.getUsername() : "system";
            repo.save(new OrderLog(order, order.getOrderNo(), order.getUser(),
                                   oldStatus, newStatus, changedBy, changedByName, trackingNo, note));
        } catch (Exception e) {
            log.error("Failed to save OrderLog orderId={}", order.getId(), e);
        }
    }

    public Page<OrderLogResponse> search(
            Integer orderId, String orderNo, String newStatus,
            LocalDateTime start, LocalDateTime end, int page, int size) {
        return repo.search(orderId, orderNo, newStatus, start, end, PageRequest.of(page, size))
                   .map(OrderLogResponse::from);
    }
}
