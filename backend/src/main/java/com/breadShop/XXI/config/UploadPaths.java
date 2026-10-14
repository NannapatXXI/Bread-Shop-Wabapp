package com.breadShop.XXI.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * ที่เก็บรูปสินค้า (โฟลเดอร์ uploads) — ใช้ร่วมกันระหว่าง WebConfig (เสิร์ฟไฟล์) และ ProductService (บันทึก/ลบไฟล์)
 *
 * กฎความปลอดภัย:
 *   - ชื่อไฟล์บน server สร้างเองจาก UUID + นามสกุลที่อนุญาต — ไม่ใช้ชื่อไฟล์ที่ client ส่งมา
 *     (ชื่อจาก client อาจมี "../" หรือ "/" หรือเป็น .html/.svg ที่รันสคริปต์ได้)
 *   - ทุก path ที่จะอ่าน/ลบต้องอยู่ในโฟลเดอร์ uploads จริง
 */
public final class UploadPaths {

    /** นามสกุลรูปที่รับ — frontend ส่งรูปหลัง crop เป็น .jpeg เสมอ */
    static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private UploadPaths() {}

    /** หาโฟลเดอร์ uploads: ./uploads ถ้ามี ไม่งั้น ../uploads (กรณีรันจากโฟลเดอร์ backend) */
    public static Path dir() {
        return resolveDir(Paths.get(System.getProperty("user.dir")));
    }

    static Path resolveDir(Path workingDir) {
        Path candidate = workingDir.resolve("uploads");
        Path parent = workingDir.getParent();
        if (candidate.toFile().exists() || parent == null) {
            return candidate.toAbsolutePath().normalize();
        }
        return parent.resolve("uploads").toAbsolutePath().normalize();
    }

    /**
     * สร้างชื่อไฟล์ใหม่ที่ปลอดภัยจากชื่อไฟล์ต้นฉบับ — เอาแค่นามสกุล (ถ้าอยู่ในรายการที่อนุญาต)
     * @throws IllegalArgumentException "INVALID_IMAGE_TYPE" ถ้าไม่ใช่ไฟล์รูปที่รองรับ
     */
    public static String newFileName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("INVALID_IMAGE_TYPE");
        }
        return UUID.randomUUID() + "." + ext;
    }

    /**
     * แปลงชื่อไฟล์ (หรือ imageUrl เช่น "uploads/x.jpg") เป็น path ในโฟลเดอร์ uploads
     * @throws IllegalArgumentException ถ้า path หลุดออกนอกโฟลเดอร์ uploads
     */
    public static Path resolveInside(Path uploadDir, String fileNameOrUrl) {
        Path fileName = Paths.get(fileNameOrUrl).getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("INVALID_FILE_PATH");
        }
        Path resolved = uploadDir.resolve(fileName.toString()).normalize();
        Path parent = resolved.getParent();
        if (parent == null || !parent.equals(uploadDir.normalize())) {
            throw new IllegalArgumentException("INVALID_FILE_PATH");
        }
        return resolved;
    }
}
