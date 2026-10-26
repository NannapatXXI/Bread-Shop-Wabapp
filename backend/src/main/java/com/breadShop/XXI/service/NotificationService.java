package com.breadShop.XXI.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.dto.notification.NotificationResponse;
import com.breadShop.XXI.entity.Notification;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.repository.NotificationRepository;

/** แจ้งเตือนลูกค้าเมื่อสถานะ order เปลี่ยน — เก็บใน DB และส่งแบบ realtime ผ่าน SSE */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService             sseService;

    public NotificationService(NotificationRepository notificationRepository, SseService sseService) {
        this.notificationRepository = notificationRepository;
        this.sseService             = sseService;
    }

    @Transactional
    public void createAndPush(Order order, OrderStatus newStatus) {
        Notification notification = new Notification(order.getUser(), order,
                message(order.getOrderNo(), newStatus), newStatus.name());
        notificationRepository.save(notification);
        sseService.send(order.getUser().getId(), toResponse(notification));
    }

    /** ใหม่สุดก่อน */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUserId(Integer userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    /** แจ้งเตือนของคนอื่น → ไม่ทำอะไร (ไม่บอกด้วยว่ามี id นี้อยู่) */
    @Transactional
    public void markAsRead(Integer notificationId, Integer userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getUser().getId().equals(userId))
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    private static String message(String orderNo, OrderStatus status) {
        String what = switch (status) {
            case CONFIRMED  -> "ได้รับการยืนยันแล้ว";
            case PROCESSING -> "กำลังเตรียมสินค้า";
            case SHIPPED    -> "ถูกจัดส่งแล้ว";
            case DELIVERED  -> "ส่งถึงมือคุณแล้ว";
            case CANCELLED  -> "ถูกยกเลิก";
            case REFUNDED   -> "ได้รับการคืนเงินแล้ว";
            default         -> "มีการอัปเดตสถานะ";
        };
        return "ออเดอร์ " + orderNo + " " + what;
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getOrder().getId(), n.getOrderNo(), n.getMessage(),
                n.getNewStatus(), n.isRead(), n.getCreatedAt());
    }
}
