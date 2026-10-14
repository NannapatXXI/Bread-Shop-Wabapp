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
 * รัน mysqldump ทุกวัน ตี 2 — เก็บไว้ 7 ไฟล์ล่าสุด
 *
 * ไฟล์ backup เก็บที่ ~/breadshop-backups/
 * ชื่อไฟล์: breadshop_2026-06-18_02-00.sql
 *
 * ต้องการ: mysqldump ติดตั้งใน PATH (มากับ MySQL)
 * ตรวจสอบ: which mysqldump
 */
@Component
public class DatabaseBackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupScheduler.class);
    private static final int    KEEP_BACKUPS = 7;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    /** คำสั่ง mysqldump (ยังไม่รัน) — แยกออกมาให้เทสตรวจได้ว่ารหัสผ่านไม่อยู่ใน argument */
    ProcessBuilder dumpCommand(String dbName, Path outputFile) {
        ProcessBuilder pb = new ProcessBuilder(
            "mysqldump",
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

    @Scheduled(cron = "0 0 2 * * *") // ตี 2 ทุกวัน
    public void backup() {
        try {
            // parse DB name จาก jdbc:mysql://localhost:3306/breadProject
            String dbName = datasourceUrl.replaceAll(".*/(\\w+)(\\?.*)?$", "$1");
            String timestamp = LocalDateTime.now().format(FMT);
            String filename = "breadshop_" + timestamp + ".sql";

            Path backupDir = Paths.get(System.getProperty("user.home"), "breadshop-backups");
            Files.createDirectories(backupDir);
            Path outputFile = backupDir.resolve(filename);

            Process process = dumpCommand(dbName, outputFile).start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                long sizeKb = Files.size(outputFile) / 1024;
                log.info("[Backup] สำเร็จ: {} ({} KB)", filename, sizeKb);
                pruneOldBackups(backupDir);
            } else {
                log.error("[Backup] ล้มเหลว exit code={} file={}", exitCode, filename);
                Files.deleteIfExists(outputFile); // ลบไฟล์เสียออก
            }

        } catch (Exception e) {
            log.error("[Backup] เกิดข้อผิดพลาด", e);
        }
    }

    // เก็บแค่ KEEP_BACKUPS ไฟล์ล่าสุด ลบเก่าออก
    private void pruneOldBackups(Path dir) {
        try {
            File[] files = dir.toFile().listFiles(f -> f.getName().endsWith(".sql"));
            if (files == null || files.length <= KEEP_BACKUPS) return;

            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int toDelete = files.length - KEEP_BACKUPS;
            for (int i = 0; i < toDelete; i++) {
                if (files[i].delete()) {
                    log.info("[Backup] ลบไฟล์เก่า: {}", files[i].getName());
                }
            }
        } catch (Exception e) {
            log.warn("[Backup] prune ล้มเหลว", e);
        }
    }
}
