package com.breadShop.XXI.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.ErrorTrendPoint;
import com.breadShop.XXI.dto.log.LogTrendPoint;
import com.breadShop.XXI.dto.log.SystemLogEntry;

/**
 * SystemLogService — อ่านและ parse log file ที่ logback เขียนไว้
 *
 * Performance strategy:
 *   1. ใช้ Files.lines() (streaming) แทน Files.readAllLines() (load all)
 *      → ไม่โหลดไฟล์ทั้งหมดลง heap ทีเดียว อ่านทีละบรรทัด
 *      → ใช้ try-with-resources กัน file handle leak
 *
 *   2. @Cacheable สำหรับ aggregate operations (trend, count)
 *      → คำนวณครั้งเดียว เก็บใน Caffeine cache 5–30 นาที
 *      → ไม่อ่านไฟล์ซ้ำทุก request
 */
// เอาไว้ parse log file ที่ logback เขียนไว้เป็น SystemLogEntry และให้บริการนับจำนวน log entries ตามระดับ log และแสดง trend ของ log entries แยกรายชั่วโมงและรายวัน 
// โดยใช้ @Cacheable เพื่อเก็บผลลัพธ์ใน cache 5–30 นาที | reviewed by peak
@Service 
public class SystemLogService {

    private static final String LOG_FILE = "logs/breadshop.log";

    private static final Pattern LINE_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[([^\\]]+)\\] (\\w+)\\s+(\\S+) - (.+)$"
    );

    // ─── readLogs: streaming + filter + paginate ────────────────
    // ไม่ cache เพราะ keyword/level/page เปลี่ยนได้ตลอด
    // return Page เพื่อให้ frontend รู้ totalElements และ totalPages
    /**
     * อ่าน log file และ filter ตาม level และ keyword จากนั้น paginate ผลลัพธ์ตาม page และ size ที่กำหนด
     * @param level ระดับ log ที่ต้องการ filter (เช่น "INFO", "WARN", "ERROR") ถ้าเป็น null หรือว่างจะไม่ filter ตาม level
     * @param keyword คำค้นหาที่ต้องการ filter ใน message หรือ logger ถ้าเป็น null หรือว่างจะไม่ filter ตาม keyword
     * @param page หมายเลขหน้าของผลลัพธ์ที่ต้องการ (เริ่มจาก 0)
     * @param size จำนวนรายการต่อหน้าของผลลัพธ์ที่ต้องการ
     * @return Page ของ SystemLogEntry ที่ตรงกับเงื่อนไขการค้นหาและ pagination
     */
    public Page<SystemLogEntry> readLogs(String level, String keyword, int page, int size) {
        Path path = Paths.get(LOG_FILE);
        if (!Files.exists(path)) return Page.empty(PageRequest.of(page, size));

        try (Stream<String> lines = Files.lines(path)) {
            List<SystemLogEntry> all = lines
                .map(this::parseLine)
                .filter(e -> e != null)
                .filter(e -> level == null || level.isBlank() || e.level().equalsIgnoreCase(level))
                .filter(e -> keyword == null || keyword.isBlank()
                          || e.message().toLowerCase().contains(keyword.toLowerCase())
                          || e.logger().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toCollection(ArrayList::new));

            Collections.reverse(all);   // newest first
            int from = page * size;
            List<SystemLogEntry> content = from >= all.size()
                ? Collections.emptyList()
                : all.subList(from, Math.min(from + size, all.size()));
            return new PageImpl<>(content, PageRequest.of(page, size), all.size());
        } catch (IOException e) {
            return Page.empty(PageRequest.of(page, size));
        }
    }

    // ─── countByLevel: cache 5 นาที ─────────────────────────────
    // key = level string เช่น "ERROR", "WARN"
    /**
     * นับจำนวน log entries ตามระดับ log ที่กำหนด (level) โดยอ่านจาก log file และใช้ @Cacheable เพื่อเก็บผลลัพธ์ใน cache 5 นาที
     * @param level ระดับ log ที่ต้องการนับ (เช่น "INFO", "WARN", "ERROR") ถ้าเป็น null หรือว่างจะคืนค่า 0
     * @return จำนวน log entries ที่ตรงกับระดับ log ที่กำหนด ถ้า log file ไม่มีหรือเกิดข้อผิดพลาดจะคืนค่า 0
     */
    @Cacheable(value = "logLevelCount", key = "#level")
    public long countByLevel(String level) {
        Path path = Paths.get(LOG_FILE);
        if (!Files.exists(path)) return 0;

        try (Stream<String> lines = Files.lines(path)) {
            return lines
                .map(this::parseLine)
                .filter(e -> e != null && e.level().equalsIgnoreCase(level))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    // ─── getTrendByHour: cache 5 นาที ───────────────────────────
    // นับ INFO/WARN/ERROR แยกรายชั่วโมง วันนี้เท่านั้น
    // ไม่มี parameter → cache key เดียว reset ทุก 5 นาที
    /**
     * นับจำนวน log entries แยกรายชั่วโมงสำหรับวันนี้ โดยนับจำนวน INFO, WARN และ ERROR ในแต่ละชั่วโมงตั้งแต่ 00:00 ถึง 23:00 และใช้ @Cacheable เพื่อเก็บผลลัพธ์ใน cache 5 นาที
     * @return List ของ LogTrendPoint ที่ประกอบด้วยชั่วโมงและจำนวน log entries สำหรับ INFO, WARN และ ERROR ในแต่ละชั่วโมง ถ้า log file ไม่มีหรือเกิดข้อผิดพลาดจะคืนค่า List ว่าง
     */
    @Cacheable("logTrend")
    public List<LogTrendPoint> getTrendByHour() {
        Path path = Paths.get(LOG_FILE);

        // สร้าง 24 slots ก่อน แม้ไม่มีข้อมูลก็ส่ง 0 กลับไป
        Map<String, long[]> slots = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            slots.put(String.format("%02d:00", h), new long[3]); // [INFO, WARN, ERROR]
        }

        if (!Files.exists(path)) return toTrendList(slots);

        String today = LocalDate.now().toString();

        try (Stream<String> lines = Files.lines(path)) {
            lines.map(this::parseLine)
                 .filter(e -> e != null && e.timestamp().startsWith(today))
                 .forEach(entry -> {
                     String hour   = entry.timestamp().substring(11, 13) + ":00";
                     long[] counts = slots.get(hour);
                     if (counts == null) return;
                     switch (entry.level().toUpperCase()) {
                         case "INFO"  -> counts[0]++;
                         case "WARN"  -> counts[1]++;
                         case "ERROR" -> counts[2]++;
                     }
                 });
        } catch (IOException ignored) {}

        return toTrendList(slots);
    }

    // ─── getErrorTrendByDay: cache 30 นาที ──────────────────────
    // นับ ERROR entries แยกรายวัน ย้อนหลัง N วัน
    // key = days parameter (7, 14, 30)
    /**
     * นับจำนวน log entries ที่มีระดับ ERROR แยกรายวันย้อนหลังตามจำนวนวันที่กำหนด (days) โดยอ่านจาก log file และใช้ @Cacheable เพื่อเก็บผลลัพธ์ใน cache 30 นาที
     * @param days จำนวนวันที่ต้องการนับย้อนหลัง (เช่น 7, 14, 30) โดยจะนับจากวันนี้ไปยังอดีต
     * @return List ของ ErrorTrendPoint ที่ประกอบด้วยวันที่และจำนวน log entries ที่มีระดับ ERROR ในแต่ละวันย้อนหลังตามจำนวนวันที่กำหนด ถ้า log file ไม่มีหรือเกิดข้อผิดพลาดจะคืนค่า List ว่าง
     */
    @Cacheable(value = "errorTrend", key = "#days")
    public List<ErrorTrendPoint> getErrorTrendByDay(int days) {
        Map<String, Long> slots = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            slots.put(LocalDate.now().minusDays(i).toString(), 0L);
        }

        Path path = Paths.get(LOG_FILE);
        if (!Files.exists(path)) return toErrorList(slots);

        try (Stream<String> lines = Files.lines(path)) {
            lines.map(this::parseLine)
                 .filter(e -> e != null && "ERROR".equalsIgnoreCase(e.level()))
                 .forEach(entry -> {
                     String date = entry.timestamp().substring(0, 10);
                     slots.computeIfPresent(date, (k, v) -> v + 1);
                 });
        } catch (IOException ignored) {}

        return toErrorList(slots);
    }

    // ─── helpers ────────────────────────────────────────────────
    /**
     * เอาไว้ parse log line เป็น SystemLogEntry โดยใช้ regex pattern ที่กำหนดไว้ ถ้าไม่ match จะคืนค่า null
     * @param line บรรทัด log ที่ต้องการ parse
     * @return SystemLogEntry ที่สร้างจากข้อมูลในบรรทัด log ถ้าไม่ match จะคืนค่า null
     */
    private SystemLogEntry parseLine(String line) {
        Matcher m = LINE_PATTERN.matcher(line);
        if (!m.matches()) return null;
        return new SystemLogEntry(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5));
    }

    /**
     * แปลง Map ของ slots ที่เก็บจำนวน log entries แยกตามชั่วโมงเป็น List ของ LogTrendPoint โดยใช้ key เป็นชั่วโมงและ value เป็น array ของจำนวน INFO, WARN, ERROR
     * @param slots Map ของ slots ที่เก็บจำนวน log entries แยกตามชั่วโมง โดย key เป็นชั่วโมง (เช่น "00:00", "01:00") และ value เป็น array ของจำนวน INFO, WARN, ERROR
     * @return List ของ LogTrendPoint ที่สร้างจากข้อมูลใน slots โดยแต่ละ LogTrendPoint จะประกอบด้วยชั่วโมงและจำนวน log entries สำหรับ INFO, WARN และ ERROR
     */
    private List<LogTrendPoint> toTrendList(Map<String, long[]> slots) {
        return slots.entrySet().stream()
            .map(e -> new LogTrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
            .collect(Collectors.toList());
    }

    /**
     * แปลง Map ของ slots ที่เก็บจำนวน log entries แยกตามวันเป็น List ของ ErrorTrendPoint โดยใช้ key เป็นวันและ value เป็นจำนวน log entries ที่มีระดับ ERROR
     * @param slots Map ของ slots ที่เก็บจำนวน log entries แยกตามวัน โดย key เป็นวัน (เช่น "2024-06-01", "2024-06-02") และ value เป็นจำนวน log entries ที่มีระดับ ERROR
     * @return List ของ ErrorTrendPoint ที่สร้างจากข้อมูลใน slots โดยแต่ละ ErrorTrendPoint จะประกอบด้วยวันและจำนวน log entries ที่มีระดับ ERROR
     */
    private List<ErrorTrendPoint> toErrorList(Map<String, Long> slots) {
        return slots.entrySet().stream()
            .map(e -> new ErrorTrendPoint(e.getKey().substring(5), e.getValue()))
            .collect(Collectors.toList());
    }
}
