package com.breadShop.XXI.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.repository.EmailOtpRepository;
import com.breadShop.XXI.repository.RefreshTokenRepository;

class SchedulerJobsTest {

    // ───────────── OtpCleanupJob ─────────────

    @Test
    void otpCleanup_deletesWithCurrentTime() {
        EmailOtpRepository repo = mock(EmailOtpRepository.class);
        when(repo.deleteExpired(any())).thenReturn(3);
        LocalDateTime before = LocalDateTime.now();

        new OtpCleanupJob(repo).cleanExpiredOtp();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deleteExpired(now.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void otpCleanup_runsEveryTenMinutes() throws Exception {
        Scheduled s = OtpCleanupJob.class.getMethod("cleanExpiredOtp").getAnnotation(Scheduled.class);
        assertThat(s.fixedRate()).isEqualTo(10 * 60 * 1000L);
    }

    // ───────────── RefreshTokenCleanupJob ─────────────

    @Test
    void refreshCleanup_deletesExpiredAndRevoked_withTheSameTimestamp() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.deleteExpired(any())).thenReturn(2);
        when(repo.deleteRevokedAndExpired(any())).thenReturn(1);

        new RefreshTokenCleanupJob(repo).cleanupExpiredTokens();

        ArgumentCaptor<LocalDateTime> a = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> b = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deleteExpired(a.capture());
        verify(repo).deleteRevokedAndExpired(b.capture());
        assertThat(a.getValue()).isEqualTo(b.getValue());
    }

    @Test
    void refreshCleanup_isTransactional_andRunsAt3am() throws Exception {
        Method m = RefreshTokenCleanupJob.class.getMethod("cleanupExpiredTokens");
        assertThat(m.getAnnotation(Transactional.class)).isNotNull();
        assertThat(m.getAnnotation(Scheduled.class).cron()).isEqualTo("0 0 3 * * *");
    }

    // ───────────── DatabaseBackupScheduler.pruneOldBackups ─────────────
    // ไม่เรียก backup() จริง เพราะจะรัน mysqldump และเขียนไฟล์ลง home ของเครื่อง

    private static File backupFile(Path dir, String name, long modifiedAt) throws IOException {
        File f = Files.writeString(dir.resolve(name), "-- dump").toFile();
        assertThat(f.setLastModified(modifiedAt)).isTrue();
        return f;
    }

    @Test
    void prune_keepsOnlyTheSevenNewestSqlFiles(@TempDir Path dir) throws Exception {
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 10; i++) {
            backupFile(dir, "breadshop_" + i + ".sql", base + i * 60_000L);
        }
        Files.writeString(dir.resolve("notes.txt"), "keep me");

        ReflectionTestUtils.invokeMethod(new DatabaseBackupScheduler(), "pruneOldBackups", dir);

        String[] left = dir.toFile().list();
        Arrays.sort(left);
        assertThat(left).containsExactly(
                "breadshop_3.sql", "breadshop_4.sql", "breadshop_5.sql", "breadshop_6.sql",
                "breadshop_7.sql", "breadshop_8.sql", "breadshop_9.sql", "notes.txt");
    }

    @Test
    void prune_doesNothing_whenSevenOrFewer(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 7; i++) {
            backupFile(dir, "b" + i + ".sql", 1_700_000_000_000L + i);
        }

        ReflectionTestUtils.invokeMethod(new DatabaseBackupScheduler(), "pruneOldBackups", dir);

        assertThat(dir.toFile().list()).hasSize(7);
    }

    @Test
    void backupCommand_passesThePasswordViaEnvironment_notOnTheCommandLine(@TempDir Path dir) {
        DatabaseBackupScheduler scheduler = new DatabaseBackupScheduler();
        ReflectionTestUtils.setField(scheduler, "dbUsername", "root");
        ReflectionTestUtils.setField(scheduler, "dbPassword", "s3cret-Pass");

        ProcessBuilder pb = scheduler.dumpCommand("breadProject", dir.resolve("out.sql"));

        // argument ของ process ใครบนเครื่องก็เห็นได้ด้วย `ps` — รหัสต้องไม่อยู่ในนั้น
        assertThat(String.join(" ", pb.command())).doesNotContain("s3cret-Pass");
        assertThat(pb.command()).containsExactly(
                "mysqldump", "-u", "root", "--single-transaction", "--routines", "--triggers", "breadProject");
        assertThat(pb.environment()).containsEntry("MYSQL_PWD", "s3cret-Pass");
        assertThat(pb.redirectOutput().file()).isEqualTo(dir.resolve("out.sql").toFile());
    }

    @Test
    void backup_runsDailyAt2am() throws Exception {
        assertThat(DatabaseBackupScheduler.class.getMethod("backup").getAnnotation(Scheduled.class).cron())
                .isEqualTo("0 0 2 * * *");
    }
}
