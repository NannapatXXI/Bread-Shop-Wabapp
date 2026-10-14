package com.breadShop.XXI.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UploadPathsTest {

    // ───────────── newFileName: ไม่ใช้ชื่อไฟล์จาก client ─────────────

    @Test
    void newFileName_isUuidPlusExtension_andDropsTheClientName() {
        String name = UploadPaths.newFileName("ครัวซองต์ หน้าร้าน.JPG");

        assertThat(name).matches("[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void newFileName_neverCarriesPathSeparatorsFromTheClient() {
        String name = UploadPaths.newFileName("../../etc/cron.d/evil.png");

        assertThat(name).doesNotContain("/").doesNotContain("..").endsWith(".png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page.html", "logo.svg", "shell.jsp", "noextension", "archive.png.exe", ""})
    void newFileName_rejectsAnythingThatIsNotAnAllowedImage(String original) {
        assertThatThrownBy(() -> UploadPaths.newFileName(original))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_IMAGE_TYPE");
    }

    @Test
    void newFileName_null_isRejected() {
        assertThatThrownBy(() -> UploadPaths.newFileName(null)).hasMessage("INVALID_IMAGE_TYPE");
    }

    // ───────────── resolveInside: อยู่ในโฟลเดอร์ uploads เท่านั้น ─────────────

    @Test
    void resolveInside_acceptsTheStoredImageUrlFormat(@TempDir Path uploads) {
        assertThat(UploadPaths.resolveInside(uploads, "uploads/abc.jpg")).isEqualTo(uploads.resolve("abc.jpg"));
        assertThat(UploadPaths.resolveInside(uploads, "abc.jpg")).isEqualTo(uploads.resolve("abc.jpg"));
    }

    @Test
    void resolveInside_stripsDirectoriesSoTraversalCannotEscape(@TempDir Path uploads) {
        Path resolved = UploadPaths.resolveInside(uploads, "uploads/../../etc/passwd");

        assertThat(resolved.getParent()).isEqualTo(uploads);
        assertThat(resolved.getFileName().toString()).isEqualTo("passwd");
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "uploads/..", "/"})
    void resolveInside_rejectsNamesThatPointOutside(String bad, @TempDir Path uploads) {
        assertThatThrownBy(() -> UploadPaths.resolveInside(uploads, bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_FILE_PATH");
    }

    // ───────────── resolveDir ─────────────

    @Test
    void resolveDir_prefersUploadsInTheWorkingDirectory(@TempDir Path root) throws Exception {
        Path backend = Files.createDirectories(root.resolve("backend"));
        Files.createDirectories(backend.resolve("uploads"));

        assertThat(UploadPaths.resolveDir(backend)).isEqualTo(backend.resolve("uploads"));
    }

    @Test
    void resolveDir_fallsBackToTheParentsUploads(@TempDir Path root) throws Exception {
        Path backend = Files.createDirectories(root.resolve("backend"));

        assertThat(UploadPaths.resolveDir(backend)).isEqualTo(root.resolve("uploads"));
    }

    @Test
    void resolveDir_atFilesystemRoot_doesNotCrash() {
        // เช่น container ที่ WORKDIR เป็น "/" — เดิม getParent() เป็น null แล้วแอปล่มตอนเริ่ม
        Path root = Path.of("/");

        assertThat(UploadPaths.resolveDir(root)).isEqualTo(Path.of("/uploads"));
    }
}
