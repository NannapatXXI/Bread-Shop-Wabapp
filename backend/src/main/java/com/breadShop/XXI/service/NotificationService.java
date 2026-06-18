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

//สำหรับการจัดการ Notification ของผู้ใช้ เช่น การสร้าง Notification ใหม่ การดึง Notification ของผู้ใช้ และการทำเครื่องหมาย 
// Notification ว่าอ่านแล้ว โดยใช้ SSE (Server-Sent Events) เพื่อส่ง Notification ไปยังผู้ใช้แบบเรียลไทม์  | reviewd by peak
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    public NotificationService(NotificationRepository notificationRepository,
                               SseService sseService) {
        this.notificationRepository = notificationRepository;
        this.sseService = sseService;
    }
    /**
     * สร้าง Notification ใหม่และส่งไปยังผู้ใช้ผ่าน SSE โดยรับพารามิเตอร์ Order และ OrderStatus ใหม่
     * @param order ออเดอร์ที่เกี่ยวข้องกับ Notification
     * @param newStatus สถานะใหม่ของออเดอร์ที่จะใช้สร้างข้อความ Notification
     */
    @Transactional
    public void createAndPush(Order order, OrderStatus newStatus) {
        String message = buildMessage(order.getOrderNo(), newStatus);
        Notification notification = new Notification(order.getUser(), order, message, newStatus.name());
        notificationRepository.save(notification);
        sseService.send(order.getUser().getId(), toResponse(notification));
    }

    /**
     * ดึง Notification ทั้งหมดของผู้ใช้ที่ระบุ โดยเรียงลำดับจากวันที่สร้างล่าสุดไปยังเก่าสุด และแปลงเป็น NotificationResponse (DTO)
     * @param userId รหัสผู้ใช้ที่ต้องการดึง Notification
     * @return List ของ NotificationResponse ที่เกี่ยวข้องกับผู้ใช้
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUserId(Integer userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()   // แปลง List เป็น stream เพื่อจะทำ operation ต่อได้
                .map(this::toResponse) // แปลงแต่ละ Notification → NotificationResponse (DTO)
                .collect(Collectors.toList()); // รวมกลับเป็น List
    }

    /**
     * ทำเครื่องหมาย Notification ที่ระบุว่าอ่านแล้ว โดยรับพารามิเตอร์ notificationId และถ้า Notification นั้นมีอยู่ จะทำการอัปเดตสถานะเป็นอ่านแล้วและบันทึกลงฐานข้อมูล
     * @param notificationId รหัสของ Notification ที่ต้องการทำเครื่องหมายว่าอ่านแล้ว
     */ 
    @Transactional
    public void markAsRead(Integer notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    /**
     * ทำเครื่องหมาย Notification ทั้งหมดของผู้ใช้ที่ระบุว่าอ่านแล้ว โดยรับพารามิเตอร์ userId และเรียกใช้ repository method ที่ทำการอัปเดตสถานะเป็นอ่านแล้วสำหรับ Notification ทั้งหมดของผู้ใช้
     * @param userId รหัสผู้ใช้ที่ต้องการทำเครื่องหมาย Notification ทั้งหมดว่าอ่านแล้ว
     */ 
    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * สร้างข้อความ Notification ตาม OrderNo และ OrderStatus ใหม่ โดยใช้ switch expression เพื่อเลือกข้อความที่เหมาะสมกับสถานะของออเดอร์
     * @param orderNo หมายเลขออเดอร์ที่เกี่ยวข้องกับ Notification
     * @param status สถานะใหม่ของออเดอร์ที่จะใช้สร้างข้อความ Notification
     * @return ข้อความ Notification ที่สร้างขึ้นตาม OrderNo และ OrderStatus ใหม่
     */
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

    /**
     * แปลง Notification entity เป็น NotificationResponse (DTO) โดยดึงข้อมูลที่จำเป็นจาก Notification และ Order ที่เกี่ยวข้อง หรือง่ายๆก็คือเอาไว้ป้องกันข้อมูลเอาออกไปแค่ข้อมูลที่จำเป็น
     * @param n Notification entity ที่ต้องการแปลงเป็น NotificationResponse
     * @return NotificationResponse ที่สร้างขึ้นจาก Notification entity
     */
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
