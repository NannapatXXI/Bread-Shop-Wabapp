package com.breadShop.XXI.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.UserActivityLogResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserActivityLog;
import com.breadShop.XXI.repository.UserActivityLogRepository;

//เอาไว้เก็บ log ของกิจกรรมผู้ใช้ | reviewd by peak
@Service
public class UserActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityLogService.class);

    private final UserActivityLogRepository repo;

    public UserActivityLogService(UserActivityLogRepository repo) {
        this.repo = repo;
    }

    /**
     * บันทึก log ของกิจกรรมผู้ใช้ โดยสร้าง UserActivityLog ใหม่และบันทึกลงฐานข้อมูล
     * @param user ผู้ใช้ที่ทำกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     * @param username ชื่อผู้ใช้ที่ทำกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     * @param action กิจกรรมที่เกิดขึ้น (เช่น "LOGIN", "LOGOUT", "UPDATE_PROFILE")
     * @param ipAddress ที่อยู่ IP ของผู้ใช้ที่ทำกิจกรรม
     * @param userAgent ข้อมูล user agent ของผู้ใช้ที่ทำกิจกรรม
     * @param details รายละเอียดเพิ่มเติมเกี่ยวกับกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     * @param status สถานะของกิจกรรม (เช่น "SUCCESS", "FAILURE")
     */
    public void log(User user, String username, String action,
                    String ipAddress, String userAgent, String details, String status) {
        try {
            repo.save(new UserActivityLog(user, username, action, ipAddress, userAgent, details, status));
        } catch (Exception e) {
            log.error("Failed to save UserActivityLog action={} user={}", action, username, e);
        }
    }

    /**
     * บันทึก log ของกิจกรรมผู้ใช้ที่สำเร็จ โดยเรียกใช้เมธอด log() และกำหนด status เป็น "SUCCESS"
     * @param user ผู้ใช้ที่ทำกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     * @param action กิจกรรมที่เกิดขึ้น (เช่น "LOGIN", "LOGOUT", "UPDATE_PROFILE")
     * @param ipAddress ที่อยู่ IP ของผู้ใช้ที่ทำกิจกรรม
     * @param userAgent ข้อมูล user agent ของผู้ใช้ที่ทำกิจกรรม
     * @param details รายละเอียดเพิ่มเติมเกี่ยวกับกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     */
    public void logSuccess(User user, String action, String ipAddress, String userAgent, String details) {
        log(user, user != null ? user.getUsername() : "anonymous", action, ipAddress, userAgent, details, "SUCCESS");
    }

    /**
     * บันทึก log ของกิจกรรมผู้ใช้ที่ล้มเหลว โดยเรียกใช้เมธอด log() และกำหนด status เป็น "FAILURE"
     * @param username ชื่อผู้ใช้ที่ทำกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     * @param action กิจกรรมที่เกิดขึ้น (เช่น "LOGIN", "LOGOUT", "UPDATE_PROFILE")
     * @param ipAddress ที่อยู่ IP ของผู้ใช้ที่ทำกิจกรรม
     * @param userAgent ข้อมูล user agent ของผู้ใช้ที่ทำกิจกรรม
     * @param details รายละเอียดเพิ่มเติมเกี่ยวกับกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     */
    public void logFailure(String username, String action, String ipAddress, String userAgent, String details) {
        log(null, username, action, ipAddress, userAgent, details, "FAILURE");
    }
    /**
     * ค้นหา log ของกิจกรรมผู้ใช้ โดยรับพารามิเตอร์ userId, action, status, keyword, start, end, page และ size และส่งกลับ Page ของ UserActivityLogResponse (DTO)
     * @param userId รหัสผู้ใช้ที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param action กิจกรรมที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param status สถานะของกิจกรรมที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param keyword คำค้นหาที่ต้องการค้นหาในรายละเอียดของกิจกรรม (สามารถเป็น null ถ้าไม่ระบุ)
     * @param start วันที่เริ่มต้นของช่วงเวลาที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param end วันที่สิ้นสุดของช่วงเวลาที่ต้องการค้นหา (สามารถเป็น null ถ้าไม่ระบุ)
     * @param page หมายเลขหน้าของผลลัพธ์ที่ต้องการ (เริ่มจาก 0)
     * @param size จำนวนรายการต่อหน้าของผลลัพธ์ที่ต้องการ
     * @return
     */
    public Page<UserActivityLogResponse> search(
            Integer userId, String action, String status, String keyword,
            LocalDateTime start, LocalDateTime end, int page, int size) {
        return repo.search(userId, action, status, keyword, start, end, PageRequest.of(page, size))
                   .map(UserActivityLogResponse::from);
    }

    /**
     * นับจำนวน log ทั้งหมดของกิจกรรมผู้ใช้ 
     * @return จำนวน log ทั้งหมดของกิจกรรมผู้ใช้
     */
    public long totalCount() {
        return repo.count();
    }

    /**
     * นับจำนวน log ของกิจกรรมผู้ใช้ที่ตรงกับ action ที่ระบุ
     * @param action กิจกรรมที่ต้องการนับจำนวน log (เช่น "LOGIN", "LOGOUT", "UPDATE_PROFILE")
     * @return จำนวน log ของกิจกรรมผู้ใช้ที่ตรงกับ action ที่ระบุ
     */
    public long countByAction(String action) {
        return repo.countByAction(action);
    }

    /**
     * นับจำนวนผู้ใช้ที่มี log ของกิจกรรมผู้ใช้ตั้งแต่เริ่มต้นของวันปัจจุบัน
     * @return จำนวนผู้ใช้ที่มี log ของกิจกรรมผู้ใช้ตั้งแต่เริ่มต้นของวันปัจจุบัน
     */
    public long countActiveUsersToday() {
        return repo.countActiveUsersSince(LocalDateTime.now().toLocalDate().atStartOfDay());
    }
}
