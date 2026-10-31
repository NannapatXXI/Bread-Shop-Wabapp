package com.breadShop.XXI.scheduler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * backup DB ด้วย mysqldump ทุกวันตี 2 → ~/breadshop-backups/breadshop_<วันเวลา>.sql เก็บ 7 ไฟล์ล่าสุด
 * ต้องมี mysqldump ในเครื่อง (มากับ MySQL) — ตั้ง path เต็มได้ที่ app.backup.mysqldump-path
 */
@Component
public class DatabaseBackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupScheduler.class);
    private static final int KEEP_BACKUPS = 7;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final String datasourceUrl;
    private final String dbUsername;
    private final String dbPassword;
    private final String mysqldumpPath;

    public DatabaseBackupScheduler(@Value("${spring.datasource.url}") String datasourceUrl,
                                   @Value("${spring.datasource.username}") String dbUsername,
                                   @Value("${DB_PASSWORD:}") String dbPassword,
                                   @Value("${app.backup.mysqldump-path:mysqldump}") String mysqldumpPath) {
        this.datasourceUrl = datasourceUrl;
        this.dbUsername    = dbUsername;
        this.dbPassword    = dbPassword;
        this.mysqldumpPath = mysqldumpPath;
    }

    /**
     * ชื่อ DB จาก JDBC URL เช่น jdbc:mysql://localhost:3306/breadProject?useSSL=false → breadProject
     * ใช้ indexOf/substring แทน regex ที่ backtrack ได้ (SonarQube java:S5852)
     */
    static String databaseName(String jdbcUrl) {
        String withoutQuery = jdbcUrl;
        int query = withoutQuery.indexOf('?');
        if (query >= 0) {
            withoutQuery = withoutQuery.substring(0, query);
        }
        String name = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
        // ชื่อนี้กลายเป็น argument ของ mysqldump — รับเฉพาะตัวอักษร ตัวเลข _
        if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            throw new IllegalStateException("อ่านชื่อ database จาก spring.datasource.url ไม่ได้");
        }
        return name;
    }

    /** คำสั่ง mysqldump (ยังไม่รัน) — แยกออกมาให้เทสตรวจได้ว่ารหัสผ่านไม่อยู่ใน argument */
    ProcessBuilder dumpCommand(String dbName, Path outputFile) {
        ProcessBuilder pb = new ProcessBuilder(
            mysqldumpPath,
            "-u", dbUsername,
            "--single-transaction",  // ไม่ lock table ระหว่าง backup
            "--routines",
            "--triggers",
            dbName
        );
        // ส่งรหัสผ่าน env ไม่ใช่ "-p<รหัส>" — argument ของ process ใครบนเครื่องก็เห็นได้ด้วย `ps`
        pb.environment().put("MYSQL_PWD", dbPassword);
        pb.redirectOutput(outputFile.toFile());
        pb.redirectErrorStream(false);
        return pb;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Bangkok")
    public void backup() {
        try {
            Path backupDir = Paths.get(System.getProperty("user.home"), "breadshop-backups");
            Files.createDirectories(backupDir);
            String filename = "breadshop_" + LocalDateTime.now().format(FILE_TIME) + ".sql";
            Path outputFile = backupDir.resolve(filename);

            int exitCode = dumpCommand(databaseName(datasourceUrl), outputFile).start().waitFor();
            if (exitCode == 0) {
                log.info("[Backup] สำเร็จ: {} ({} KB)", filename, Files.size(outputFile) / 1024);
                pruneOldBackups(backupDir);
            } else {
                log.error("[Backup] ล้มเหลว exit code={} file={}", exitCode, filename);
                Files.deleteIfExists(outputFile);   // ไฟล์ครึ่ง ๆ กลาง ๆ ใช้กู้คืนไม่ได้
            }
        } catch (Exception e) {
            log.error("[Backup] เกิดข้อผิดพลาด", e);
        }
    }

    /** เก็บแค่ KEEP_BACKUPS ไฟล์ใหม่สุด */
    private void pruneOldBackups(Path dir) {
        File[] files = dir.toFile().listFiles(f -> f.getName().endsWith(".sql"));
        if (files == null || files.length <= KEEP_BACKUPS) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - KEEP_BACKUPS; i++) {
            if (files[i].delete()) {
                log.info("[Backup] ลบไฟล์เก่า: {}", files[i].getName());
            }
        }
    }
}
