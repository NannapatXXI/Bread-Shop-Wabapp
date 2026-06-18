package com.breadShop.XXI.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.AuditLogResponse;
import com.breadShop.XXI.entity.AuditLog;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.AuditLogRepository;

//สำหรับการบันทึกและค้นหาประวัติการกระทำของผู้ใช้หรือระบบ reviewd by peak
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /**
     * บันทึกการกระทำของผู้ใช้หรือระบบ
     * @return ไม่มีค่าคืน แต่จะบันทึกข้อมูลการกระทำลงในฐานข้อมูลผ่าน AuditLogRepository โดยจะจับข้อผิดพลาดที่อาจเกิดขึ้นและบันทึกลงใน log ด้วย
     */ 
    public void log(User actor, String action, String entityType, String entityId,
                    String oldValue, String newValue, String details) {
        try {
            String actorName = actor != null ? actor.getUsername() : "system";
            String actorRole = actor != null ? actor.getRole() : "SYSTEM";
            repo.save(new AuditLog(actor, actorName, actorRole, action,
                                   entityType, entityId, oldValue, newValue, details));
        } catch (Exception e) {
            log.error("Failed to save AuditLog action={} entity={}/{}", action, entityType, entityId, e);
        }
    }

    /**
     * ค้นหาประวัติการกระทำของผู้ใช้หรือระบบตามเงื่อนไขต่างๆ เช่น actorId, action, entityType, keyword ในรายละเอียด, ช่วงเวลาที่เกิดการกระทำ และการแบ่งหน้า (pagination) โดยจะส่งคืนผลลัพธ์ในรูปแบบ Page<AuditLogResponse> ซึ่งเป็น DTO ที่มีข้อมูลที่จำเป็นสำหรับ client เท่านั้น โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ
     * @param actorId ID ของผู้กระทำการ (actor) ที่ต้องการค้นหา หากไม่ต้องการกรองตาม actor ให้ส่งค่า null
     * @param action ประเภทของการกระทำที่ต้องการค้นหา เช่น "CREATE", "UPDATE", "DELETE" หากไม่ต้องการกรองตาม action ให้ส่งค่า null
     * @param entityType ประเภทของ entity ที่เกี่ยวข้องกับการกระทำ เช่น "User", "Product" หากไม่ต้องการกรองตาม entityType ให้ส่งค่า null
     * @param keyword คำค้นหาที่จะใช้ค้นหาในรายละเอียดของการกระทำ หากไม่ต้องการกรองตาม keyword ให้ส่งค่า null
     * @param start ช่วงเวลาที่เริ่มต้นสำหรับการค้นหา (เช่น 2024-01-01T00:00:00) หากไม่ต้องการกรองตามช่วงเวลา ให้ส่งค่า null
     * @param end ช่วงเวลาที่สิ้นสุดสำหรับการค้นหา (เช่น 2024-12-31T23:59:59) หากไม่ต้องการกรองตามช่วงเวลา ให้ส่งค่า null 
     * @param page หมายเลขหน้าที่ต้องการดึงข้อมูล (เริ่มต้นที่ 0) สำหรับการแบ่งหน้า (pagination)
     * @param size จำนวนรายการต่อหน้าที่ต้องการดึงข้อมูล สำหรับการแบ่งหน้า (pagination) 
     * @return ผลลัพธ์การค้นหาในรูปแบบ Page<AuditLogResponse> ซึ่งเป็น DTO ที่มีข้อมูลที่จำเป็นสำหรับ client เท่านั้น โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ และรองรับการแบ่งหน้า (pagination) ตามพารามิเตอร์ page และ size ที่ส่งเข้ามา
     */
    public Page<AuditLogResponse> search(
            Integer actorId, String action, String entityType, String keyword,
            LocalDateTime start, LocalDateTime end, int page, int size) {
        return repo.search(actorId, action, entityType, keyword, start, end, PageRequest.of(page, size))
                   .map(AuditLogResponse::from);
    }

    /**
     * นับจำนวนการกระทำทั้งหมดที่ถูกบันทึกในระบบ ที่มีอยู่ในฐานข้อมูลผ่าน AuditLogRepository
     * @return จำนวนการกระทำทั้งหมดที่ถูกบันทึกในระบบมีอยู่ในฐานข้อมูลผ่าน AuditLogRepository
     */
    public long total() {
        return repo.count();
    }
}
