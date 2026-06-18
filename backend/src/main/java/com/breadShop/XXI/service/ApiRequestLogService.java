package com.breadShop.XXI.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.ApiStatResponse;
import com.breadShop.XXI.dto.log.ErrorTrendPoint;
import com.breadShop.XXI.dto.log.FailedRequestResponse;
import com.breadShop.XXI.entity.ApiRequestLog;
import com.breadShop.XXI.repository.ApiRequestLogRepository;

//สำหรับการบันทึกและวิเคราะห์สถิติการเรียก API   reviewd by peak
@Service
public class ApiRequestLogService {

    // เอาไว้เขีัยน log ในกรณีที่เกิดข้อผิดพลาดในการบันทึกหรือดึงข้อมูลสถิติการเรียก API เป็น logging facade ที่นิยมใช้ใน Java และสามารถทำงานร่วมกับหลายๆ logging framework เช่น Logback หรือ Log4j ได้อย่างยืดหยุ่น
    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogService.class); 
    
    private final ApiRequestLogRepository repo;

    public ApiRequestLogService(ApiRequestLogRepository repo) {
        this.repo = repo;
    }

    public void save(String method, String endpoint, String uri,
                     int statusCode, long durationMs, String userEmail, String ipAddress) {
        try {
            repo.save(new ApiRequestLog(method, endpoint, uri, statusCode, durationMs, userEmail, ipAddress));
        } catch (Exception e) {
            log.error("Failed to save ApiRequestLog", e);
        }
    }

    /**
     * ดึงสถิติการเรียก API แยกตาม method และ endpoint โดยรวมข้อมูลทั้งหมด เช่น จำนวนครั้งที่เรียก, ค่าเฉลี่ยเวลาตอบสนอง, จำนวนครั้งที่สำเร็จ (status code < 400) และจำนวนครั้งที่เกิดข้อผิดพลาด (status code >= 400) รวมถึงคำนวณอัตราความสำเร็จและอัตราข้อผิดพลาด
     * @return  รายการสถิติ API ในรูปแบบ List<ApiStatResponse> ซึ่งเป็น DTO ที่มีข้อมูลที่จำเป็นสำหรับ client เท่านั้น โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ
     */
    public List<ApiStatResponse> getApiStats() {
        return repo.getApiStats().stream().map(row -> {
            long total   = ((Number) row[2]).longValue();
            long avgMs   = ((Number) row[3]).longValue();
            long success = ((Number) row[4]).longValue();
            long errors  = ((Number) row[5]).longValue();
            double successRate = total > 0 ? Math.round(success * 1000.0 / total) / 10.0 : 0;
            double errorRate   = total > 0 ? Math.round(errors  * 1000.0 / total) / 10.0 : 0;
            return new ApiStatResponse(
                (String) row[0], (String) row[1],
                total, avgMs, success, errors, successRate, errorRate
            );
        }).collect(Collectors.toList());
    }

    /**
     * ดึงรายการเรียก API ที่ล้มเหลว (status code >= 400) โดยเรียงลำดับจากล่าสุดไปเก่าสุด และรองรับการแบ่งหน้า (pagination) โดยรับพารามิเตอร์ page และ size เพื่อกำหนดหน้าที่ต้องการและจำนวนรายการต่อหน้า
     * @param page หมายเลขหน้าที่ต้องการ (เริ่มต้นที่ 0)
     * @param size จำนวนรายการต่อหน้า
     * @return รายการเรียก API ที่ล้มเหลวในรูปแบบ Page<FailedRequestResponse> ซึ่งเป็น DTO ที่มีข้อมูลที่จำเป็นสำหรับ client เท่านั้น โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ และยังมีข้อมูลเกี่ยวกับการแบ่งหน้า เช่น จำนวนหน้าทั้งหมดและจำนวนรายการทั้งหมด
     */
    public Page<FailedRequestResponse> getFailedRequests(int page, int size) {
        return repo.findByStatusCodeGreaterThanEqualOrderByCreatedAtDesc(400, PageRequest.of(page, size))
                   .map(FailedRequestResponse::from);
    }


    /**
     * ดึงสถิติการเกิดข้อผิดพลาดแยกตามประเภทของข้อผิดพลาด (เช่น 404 Not Found, 500 Internal Server Error) โดยนับจำนวนครั้งที่เกิดข้อผิดพลาดแต่ละประเภท และจัดเรียงจากมากไปน้อย
     * @return รายการสถิติข้อผิดพลาดในรูปแบบ List<Map<String, Object>> ซึ่งแต่ละรายการเป็นแผนที่ที่มีคีย์ "name" สำหรับชื่อของข้อผิดพลาด (เช่น "404 Not Found") และคีย์ "value" สำหรับจำนวนครั้งที่เกิดข้อผิดพลาดนั้นๆ โดยข้อมูลนี้จะถูกใช้สำหรับแสดงกราฟหรือรายงานสถิติข้อผิดพลาดในส่วนของ client
     */
    public List<Map<String, Object>> getErrorBreakdown() {
        return repo.getErrorBreakdown().stream().map(row ->
            Map.<String, Object>of("name", row[0], "value", ((Number) row[1]).longValue())
        ).collect(Collectors.toList());
    }

    /**
     * ดึงแนวโน้มการเกิดข้อผิดพลาดในช่วงเวลาที่กำหนด โดยรับพารามิเตอร์ days เพื่อระบุจำนวนวันที่ต้องการดูแนวโน้ม และคืนค่ารายการจุดข้อมูลที่แสดงจำนวนข้อผิดพลาดในแต่ละวัน โดยข้อมูลนี้จะถูกใช้สำหรับแสดงกราฟแนวโน้มข้อผิดพลาดในส่วนของ client
     * @param days จำนวนวันที่ต้องการดูแนวโน้ม (เช่น 7 สำหรับ 7 วันที่ผ่านมา)
     * @return รายการจุดข้อมูลแนวโน้มข้อผิดพลาดในรูปแบบ List<ErrorTrendPoint> ซึ่งแต่ละจุดข้อมูลมีคีย์ "date" สำหรับวันที่ (ในรูปแบบ String) และคีย์ "count" สำหรับจำนวนข้อผิดพลาดที่เกิดขึ้นในวันนั้นๆ โดยข้อมูลนี้จะถูกใช้สำหรับแสดงกราฟแนวโน้มข้อผิดพลาดในส่วนของ client
     */
    public List<ErrorTrendPoint> getErrorTrend(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return repo.getErrorTrendSince(since).stream().map(row ->
            new ErrorTrendPoint(row[0].toString(), ((Number) row[1]).longValue())
        ).collect(Collectors.toList());
    }

    /**
     * ดึงจำนวนครั้งที่มีการเรียก API ทั้งหมด และจำนวนครั้งที่เกิดข้อผิดพลาดทั้งหมด โดยข้อมูลนี้จะถูกใช้สำหรับแสดงสถิติภาพรวมของการเรียก API ในส่วนของ client
     * @return แผนที่ที่มีคีย์ "totalRequests" สำหรับจำนวนครั้งที่มีการเรียก API ทั้งหมด และคีย์ "totalErrors" สำหรับจำนวนครั้งที่เกิดข้อผิดพลาดทั้งหมด โดยข้อมูลนี้จะถูกใช้สำหรับแสดงสถิติภาพรวมของการเรียก API ในส่วนของ client
     */
    public long totalRequests() {
        return repo.count();
    }
    /**
     * ดึงจำนวนครั้งที่เกิดข้อผิดพลาดทั้งหมด (status code >= 400) โดยข้อมูลนี้จะถูกใช้สำหรับแสดงสถิติภาพรวมของการเรียก API ในส่วนของ client
     * @return จำนวนครั้งที่เกิดข้อผิดพลาดทั้งหมด (status code >= 400) โดยข้อมูลนี้จะถูกใช้สำหรับแสดงสถิติภาพรวมของการเรียก API ในส่วนของ client
     */
    public long totalErrors() {
        return repo.countByStatusCodeGreaterThanEqual(400);
    }
}
