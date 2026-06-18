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

//เอาไว้เก็บ log ของ order | reviewd by peak
@Service
public class OrderLogService {

    private static final Logger log = LoggerFactory.getLogger(OrderLogService.class);

    private final OrderLogRepository repo;

    public OrderLogService(OrderLogRepository repo) {
        this.repo = repo;
    }

    /**
     * บันทึก log ของ order โดยสร้าง OrderLog ใหม่และบันทึกลงฐานข้อมูล โดยรับพารามิเตอร์ order, oldStatus, newStatus, changedBy, trackingNo และ note
     * @param order ออเดอร์ที่่ต้องการบันทึก log
     * @param oldStatus สถานะเก่าของออเดอร์ก่อนเปลี่ยนแปลง
     * @param newStatus สถานะใหม่ของออเดอร์หลังเปลี่ยนแปลง
     * @param changedBy ผู้ใช้ที่ทำการเปลี่ยนแปลงสถานะของออเดอร์ (สามารถเป็น null ถ้าเปลี่ยนโดยระบบ)
     * @param trackingNo หมายเลขติดตามพัสดุของออเดอร์ (สามารถเป็น null ถ้าไม่มี)
     * @param note หมายเหตุเพิ่มเติมเกี่ยวกับการเปลี่ยนแปลงสถานะของออเดอร์ (สามารถเป็น null ถ้าไม่มี)
     */
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

    /**
     * ค้นหา log ของ order โดยรับพารามิเตอร์ orderId, orderNo, newStatus, start, end, page และ size และส่งกลับ Page ของ OrderLogResponse (DTO)
     * @param orderId order id ที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param orderNo order number ที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param newStatus สถานะใหม่ของออเดอร์ที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param start วันที่เริ่มต้นของช่วงเวลาที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param end วันที่สิ้นสุดของช่วงเวลาที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param page หมายเลขหน้าของผลลัพธ์ที่ต้องการ (เริ่มจาก 0)
     * @param size จำนวนรายการต่อหน้าของผลลัพธ์ที่ต้องการ
     * @return Page ของ OrderLogResponse ที่ตรงกับเงื่อนไขการค้นหา
     */
    public Page<OrderLogResponse> search(
            Integer orderId, String orderNo, String newStatus,
            LocalDateTime start, LocalDateTime end, int page, int size) {
        return repo.search(orderId, orderNo, newStatus, start, end, PageRequest.of(page, size))
                   .map(OrderLogResponse::from);
    }
}
