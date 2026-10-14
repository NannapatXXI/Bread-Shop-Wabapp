package com.breadShop.XXI.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import com.breadShop.XXI.dto.log.ErrorTrendPoint;
import com.breadShop.XXI.dto.log.LogTrendPoint;
import com.breadShop.XXI.dto.log.SystemLogEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SystemLogService อ่านไฟล์จริงจาก "logs/breadshop.log" (relative path)
 * เทสนี้จึงสำรองไฟล์เดิมก่อนเขียนทับด้วยข้อมูลทดสอบ แล้วคืนค่าเดิมกลับหลังจบแต่ละเทส
 * เพื่อไม่ให้ log จริงของแอปหายไป
 */
class SystemLogServiceTest {

    private static final Path LOG_PATH = Paths.get("logs/breadshop.log");

    private SystemLogService systemLogService;
    private boolean existedBefore;
    private byte[] originalContent;

    @BeforeEach
    void setUp() throws IOException {
        systemLogService = new SystemLogService();
        existedBefore = Files.exists(LOG_PATH);
        if (existedBefore) {
            originalContent = Files.readAllBytes(LOG_PATH);
        } else if (LOG_PATH.getParent() != null) {
            Files.createDirectories(LOG_PATH.getParent());
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (existedBefore) {
            Files.write(LOG_PATH, originalContent);
        } else {
            Files.deleteIfExists(LOG_PATH);
        }
    }

    private void writeLogLines(String... lines) throws IOException {
        Files.write(LOG_PATH, String.join(System.lineSeparator(), lines).getBytes());
    }

    @Test
    void readLogs_whenFileMissing_shouldReturnEmptyPage() throws IOException {
        Files.deleteIfExists(LOG_PATH);

        Page<SystemLogEntry> result = systemLogService.readLogs(null, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void readLogs_shouldFilterByLevelAndKeywordAndPaginateNewestFirst() throws IOException {
        writeLogLines(
            "2024-06-01 10:00:00.000 [main] INFO  com.breadShop.XXI.A - first message",
            "2024-06-01 10:00:01.000 [main] ERROR com.breadShop.XXI.B - boom happened",
            "2024-06-01 10:00:02.000 [main] INFO  com.breadShop.XXI.C - third message"
        );

        Page<SystemLogEntry> onlyErrors = systemLogService.readLogs("ERROR", null, 0, 10);
        assertThat(onlyErrors.getContent()).hasSize(1);
        assertEquals("boom happened", onlyErrors.getContent().get(0).message());

        Page<SystemLogEntry> byKeyword = systemLogService.readLogs(null, "third", 0, 10);
        assertThat(byKeyword.getContent()).hasSize(1);

        Page<SystemLogEntry> all = systemLogService.readLogs(null, null, 0, 10);
        assertThat(all.getTotalElements()).isEqualTo(3);
        assertEquals("third message", all.getContent().get(0).message());
    }

    @Test
    void readLogs_whenPageOutOfRange_shouldReturnEmptyContent() throws IOException {
        writeLogLines("2024-06-01 10:00:00.000 [main] INFO  com.breadShop.XXI.A - only line");

        Page<SystemLogEntry> result = systemLogService.readLogs(null, null, 5, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void countByLevel_whenFileMissing_shouldReturnZero() throws IOException {
        Files.deleteIfExists(LOG_PATH);

        assertEquals(0, systemLogService.countByLevel("ERROR"));
    }

    @Test
    void countByLevel_shouldCountMatchingLevelCaseInsensitive() throws IOException {
        writeLogLines(
            "2024-06-01 10:00:00.000 [main] ERROR com.breadShop.XXI.A - err1",
            "2024-06-01 10:00:01.000 [main] error com.breadShop.XXI.B - err2",
            "2024-06-01 10:00:02.000 [main] INFO  com.breadShop.XXI.C - info1"
        );

        assertEquals(2, systemLogService.countByLevel("ERROR"));
    }

    @Test
    void getTrendByHour_shouldCountTodaysEntriesPerHour() throws IOException {
        String today = LocalDate.now().toString();
        writeLogLines(
            today + " 01:00:00.000 [main] INFO  com.breadShop.XXI.A - info at 1am",
            today + " 01:00:01.000 [main] ERROR com.breadShop.XXI.B - error at 1am",
            "2000-01-01 01:00:00.000 [main] INFO  com.breadShop.XXI.C - old entry ignored"
        );

        List<LogTrendPoint> trend = systemLogService.getTrendByHour();

        assertThat(trend).hasSize(24);
        LogTrendPoint oneAm = trend.stream().filter(t -> t.time().equals("01:00")).findFirst().orElseThrow();
        assertEquals(1, oneAm.info());
        assertEquals(1, oneAm.error());
    }

    @Test
    void getTrendByHour_whenFileMissing_shouldReturnAllZeroSlots() throws IOException {
        Files.deleteIfExists(LOG_PATH);

        List<LogTrendPoint> trend = systemLogService.getTrendByHour();

        assertThat(trend).hasSize(24);
        assertThat(trend).allMatch(t -> t.info() == 0 && t.warn() == 0 && t.error() == 0);
    }

    @Test
    void getErrorTrendByDay_shouldCountErrorsPerDayWithinRange() throws IOException {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        writeLogLines(
            today + " 10:00:00.000 [main] ERROR com.breadShop.XXI.A - err today",
            yesterday + " 10:00:00.000 [main] ERROR com.breadShop.XXI.B - err yesterday",
            "2000-01-01 10:00:00.000 [main] ERROR com.breadShop.XXI.C - too old to count"
        );

        List<ErrorTrendPoint> trend = systemLogService.getErrorTrendByDay(3);

        assertThat(trend).hasSize(3);
        long total = trend.stream().mapToLong(ErrorTrendPoint::errors).sum();
        assertEquals(2, total);
    }

    @Test
    void readLogs_withMalformedLine_shouldSkipUnparsableLines() throws IOException {
        writeLogLines(
            "this is not a valid log line at all",
            "2024-06-01 10:00:00.000 [main] INFO  com.breadShop.XXI.A - valid message"
        );

        Page<SystemLogEntry> result = systemLogService.readLogs(null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertEquals("valid message", result.getContent().get(0).message());
    }

    @Test
    void countByLevel_withMalformedLine_shouldIgnoreIt() throws IOException {
        writeLogLines(
            "garbage line that does not match pattern",
            "2024-06-01 10:00:00.000 [main] ERROR com.breadShop.XXI.A - err1"
        );

        assertEquals(1, systemLogService.countByLevel("ERROR"));
    }

    @Test
    void getTrendByHour_shouldCountWarnAndIgnoreUnknownLevels() throws IOException {
        String today = LocalDate.now().toString();
        writeLogLines(
            today + " 02:00:00.000 [main] WARN  com.breadShop.XXI.A - warn at 2am",
            today + " 02:00:01.000 [main] DEBUG com.breadShop.XXI.B - debug not counted"
        );

        List<LogTrendPoint> trend = systemLogService.getTrendByHour();

        LogTrendPoint twoAm = trend.stream().filter(t -> t.time().equals("02:00")).findFirst().orElseThrow();
        assertEquals(1, twoAm.warn());
        assertEquals(0, twoAm.info());
        assertEquals(0, twoAm.error());
    }

    @Test
    void getErrorTrendByDay_whenFileMissing_shouldReturnAllZeroSlots() throws IOException {
        Files.deleteIfExists(LOG_PATH);

        List<ErrorTrendPoint> trend = systemLogService.getErrorTrendByDay(3);

        assertThat(trend).hasSize(3);
        assertThat(trend).allMatch(t -> t.errors() == 0);
    }
}
