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
import java.util.stream.Stream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.repository.AuditLogRepository;
import com.breadShop.XXI.repository.EmailOtpRepository;
import com.breadShop.XXI.repository.RefreshTokenRepository;
import com.breadShop.XXI.repository.UserActivityLogRepository;

class SchedulerJobsTest {

    private static DatabaseBackupScheduler scheduler(String user, String password, String mysqldumpPath) {
        return new DatabaseBackupScheduler("jdbc:mysql://localhost:3306/breadProject", user, password, mysqldumpPath);
    }


    // ───────────── ทุก job ─────────────

    /** งานที่ระบุเวลาบนนาฬิกา (cron) ต้องใช้เวลาไทย — ไม่งั้นบนเซิร์ฟเวอร์ UTC "ตี 2" จะกลายเป็น 9 โมงเช้า */
    @Test
    void everyCronJob_runsOnBangkokTime() {
        List<Scheduled> cronJobs = Stream.of(OtpCleanupJob.class, RefreshTokenCleanupJob.class,
                        LogRetentionJob.class, DatabaseBackupScheduler.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .map(m -> m.getAnnotation(Scheduled.class))
                .filter(s -> s != null && !s.cron().isEmpty())
                .toList();

        assertThat(cronJobs).hasSize(3);
        assertThat(cronJobs).allSatisfy(s -> assertThat(s.zone()).isEqualTo("Asia/Bangkok"));
    }

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

    // ───────────── LogRetentionJob ─────────────

    @Test
    void logRetention_deletesEachTableWithItsOwnCutoff() {
        UserActivityLogRepository activity = mock(UserActivityLogRepository.class);
        AuditLogRepository audit = mock(AuditLogRepository.class);
        LocalDateTime before = LocalDateTime.now();

        new LogRetentionJob(activity, audit, 90, 365).purgeOldLogs();

        ArgumentCaptor<LocalDateTime> activityCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> auditCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activity).deleteOlderThan(activityCutoff.capture());
        verify(audit).deleteOlderThan(auditCutoff.capture());
        LocalDateTime after = LocalDateTime.now();
        assertThat(activityCutoff.getValue()).isBetween(before.minusDays(90), after.minusDays(90));
        assertThat(auditCutoff.getValue()).isBetween(before.minusDays(365), after.minusDays(365));
    }

    @Test
    void logRetention_runsDailyAfterTheBackup() throws Exception {
        Scheduled retention = LogRetentionJob.class.getMethod("purgeOldLogs").getAnnotation(Scheduled.class);
        Scheduled backup = Arrays.stream(DatabaseBackupScheduler.class.getMethods())
                .map(m -> m.getAnnotation(Scheduled.class)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertThat(retention.cron()).isEqualTo("0 0 4 * * *");
        assertThat(backup.cron()).isEqualTo("0 0 2 * * *");
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

        ReflectionTestUtils.invokeMethod(scheduler("root", "", "mysqldump"), "pruneOldBackups", dir);

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

        ReflectionTestUtils.invokeMethod(scheduler("root", "", "mysqldump"), "pruneOldBackups", dir);

        assertThat(dir.toFile().list()).hasSize(7);
    }

    @Test
    void backupCommand_passesThePasswordViaEnvironment_notOnTheCommandLine(@TempDir Path dir) {
        DatabaseBackupScheduler scheduler = scheduler("root", "s3cret-Pass", "mysqldump");

        ProcessBuilder pb = scheduler.dumpCommand("breadProject", dir.resolve("out.sql"));

        // argument ของ process ใครบนเครื่องก็เห็นได้ด้วย `ps` — รหัสต้องไม่อยู่ในนั้น
        assertThat(String.join(" ", pb.command())).doesNotContain("s3cret-Pass");
        assertThat(pb.command()).containsExactly(
                "mysqldump", "-u", "root", "--single-transaction", "--routines", "--triggers", "breadProject");
        assertThat(pb.environment()).containsEntry("MYSQL_PWD", "s3cret-Pass");
        assertThat(pb.redirectOutput().file()).isEqualTo(dir.resolve("out.sql").toFile());
    }

    @Test
    void backupCommand_usesTheConfiguredMysqldumpPath(@TempDir Path dir) {
        DatabaseBackupScheduler scheduler = scheduler("root", "", "/opt/homebrew/bin/mysqldump");

        assertThat(scheduler.dumpCommand("breadProject", dir.resolve("out.sql")).command().get(0))
                .isEqualTo("/opt/homebrew/bin/mysqldump");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "jdbc:mysql://localhost:3306/breadProject,                                   breadProject",
        "jdbc:mysql://db:3306/breadProject?useSSL=false&serverTimezone=Asia/Bangkok,  breadProject",
        "jdbc:mysql://localhost/shop_2026,                                            shop_2026",
    })
    void databaseName_readsTheNameAfterTheLastSlash_ignoringQuery(String url, String expected) {
        assertThat(DatabaseBackupScheduler.databaseName(url)).isEqualTo(expected);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "jdbc:mysql://localhost:3306/", "jdbc:mysql://localhost:3306/bread;drop", "jdbc:mysql://h/--all-databases"
    })
    void databaseName_rejectsAnythingThatIsNotAPlainName(String url) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DatabaseBackupScheduler.databaseName(url))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void databaseName_handlesVeryLongInputQuickly() {
        String hostile = "jdbc:mysql://" + "a/".repeat(50_000) + "x".repeat(50_000) + "?";
        long start = System.nanoTime();

        DatabaseBackupScheduler.databaseName(hostile);

        assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).isLessThan(java.time.Duration.ofSeconds(1));
    }

    @Test
    void backup_runsDailyAt2am() throws Exception {
        assertThat(DatabaseBackupScheduler.class.getMethod("backup").getAnnotation(Scheduled.class).cron())
                .isEqualTo("0 0 2 * * *");
    }
}
