package com.breadShop.XXI.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.breadShop.XXI.config.UploadPaths;

/**
 * เก็บ/ลบไฟล์รูปในโฟลเดอร์ uploads — DB เก็บแค่ path ("uploads/<uuid>.jpg")
 * ใช้ร่วมกันทั้งรูปสินค้าและรูปปกหน้าร้าน กฎความปลอดภัยจึงอยู่ที่เดียว (ดู UploadPaths)
 */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);
    private static final Path UPLOAD_DIR = UploadPaths.dir();

    public static boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    /**
     * บันทึกด้วยชื่อที่ server สร้างเอง (ไม่ใช้ชื่อไฟล์จาก client)
     * @return path ที่เก็บใน DB
     * @throws IllegalArgumentException "INVALID_IMAGE_TYPE" ถ้าไม่ใช่ไฟล์รูปที่รองรับ
     */
    public String save(MultipartFile image) throws IOException {
        String fileName = UploadPaths.newFileName(image.getOriginalFilename());
        image.transferTo(UploadPaths.resolveInside(UPLOAD_DIR, fileName).toFile());
        return "uploads/" + fileName;
    }

    /** ลบไม่สำเร็จแค่ log ไว้ — ไม่ควรทำให้งานหลัก (แก้สินค้า/เปลี่ยนรูปปก) ล้ม */
    public void delete(String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        try {
            Files.deleteIfExists(UploadPaths.resolveInside(UPLOAD_DIR, imageUrl));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Delete image failed: {}", e.getMessage());
        }
    }
}
