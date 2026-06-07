package com.breadShop.XXI.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.dto.notification.NotificationResponse;
import com.breadShop.XXI.entity.Notification;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    public NotificationService(NotificationRepository notificationRepository,
                               SseService sseService) {
        this.notificationRepository = notificationRepository;
        this.sseService = sseService;
    }

    @Transactional
    public void createAndPush(Order order, OrderStatus newStatus) {
        String message = buildMessage(order.getOrderNo(), newStatus);
        Notification notification = new Notification(order.getUser(), order, message, newStatus.name());
        notificationRepository.save(notification);
        sseService.send(order.getUser().getId(), toResponse(notification));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUserId(Integer userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(Integer notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    private String buildMessage(String orderNo, OrderStatus status) {
        return switch (status) {
            case CONFIRMED   -> "ออเดอร์ " + orderNo + " ได้รับการยืนยันแล้ว";
            case PROCESSING  -> "ออเดอร์ " + orderNo + " กำลังเตรียมสินค้า";
            case SHIPPED     -> "ออเดอร์ " + orderNo + " ถูกจัดส่งแล้ว";
            case DELIVERED   -> "ออเดอร์ " + orderNo + " ส่งถึงมือคุณแล้ว";
            case CANCELLED   -> "ออเดอร์ " + orderNo + " ถูกยกเลิก";
            case REFUNDED    -> "ออเดอร์ " + orderNo + " ได้รับการคืนเงินแล้ว";
            default          -> "ออเดอร์ " + orderNo + " มีการอัปเดตสถานะ";
        };
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getOrder().getId(),
                n.getOrderNo(),
                n.getMessage(),
                n.getNewStatus(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
