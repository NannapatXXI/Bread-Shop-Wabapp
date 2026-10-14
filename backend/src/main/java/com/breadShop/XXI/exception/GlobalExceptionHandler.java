package com.breadShop.XXI.exception;

import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.breadShop.XXI.dto.ApiResponse;

/**
 * [Claude] GlobalExceptionHandler — จัดการ exception ทุกประเภทให้คืน ApiResponse<Void> เสมอ
 *
 * เหตุผลที่ต้องมี:
 *   - ถ้าไม่มีตัวนี้ Spring จะคืน error ในรูปแบบ default ที่ไม่ match กับ ApiResponse
 *   - ทำให้ frontend รู้ว่า success=false และ message มีอะไรผิด
 *
 * Pattern ทุก handler: ResponseEntity<ApiResponse<Void>>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid validation ล้มเหลว — รวม error ทุก field เป็น message เดียว */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg));
    }

    /** @Valid กับ @ModelAttribute (เช่น multipart form) ล้มเหลว */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg));
    }

    /** request ผิดรูปแบบ — ขาด param, ชนิดข้อมูลผิด, JSON อ่านไม่ได้ → 400 (ไม่ใช่ 500) */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("ข้อมูลที่ส่งมาไม่ถูกต้อง"));
    }

    /** ชื่อ/รหัสผ่านผิด */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Username หรือ Password ไม่ถูกต้อง"));
    }

    /** 404, 403 ที่โยนจาก service ด้วย ResponseStatusException */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getReason()));
    }

    /**
     * IllegalArgumentException — ใช้ทั่วโปรเจกต์แทน custom exception
     * ค่า message ที่กำหนดไว้เป็น key เพื่อแปลเป็น error message ภาษาไทย
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = switch (Objects.toString(ex.getMessage(), "")) {
            case "USER_NOT_FOUND"    -> "ไม่พบผู้ใช้ในระบบ";
            case "EMAIL_NOT_FOUND"   -> "ไม่พบ Email ในระบบ";
            case "USERNAME_EXISTS"   -> "มีชื่อผู้ใช้นี้ในระบบแล้ว";
            case "EMAIL_EXISTS"      -> "มี Email นี้ในระบบแล้ว";
            case "OTP_NOT_FOUND"     -> "OTP ไม่ถูกต้อง";
            case "OTP_EXPIRED"       -> "OTP หมดเวลา";
            case "OTP_INVALID"       -> "OTP ไม่ถูกต้อง";
            case "OTP_LOCKED"        -> "OTP ถูกล็อก กรุณาขอ OTP ใหม่";
            case "EMAIL_REQUIRED"    -> "กรุณากรอก Email";
            case "INVALID_IMAGE_TYPE" -> "รองรับเฉพาะไฟล์รูป jpg, png, webp, gif";
            case "Product not found" -> "ไม่พบสินค้า";
            case "GOOGLE_ACCOUNT"    -> "GOOGLE_ACCOUNT"; // frontend เช็คค่านี้เพื่อแนะนำให้ login ด้วย Google
            default                  -> null;
        };
        if (msg == null) {
            // ข้อความที่ไม่รู้จักอาจมาจาก library (มีชื่อ class ภายใน) — log ไว้ฝั่ง server เท่านั้น
            log.warn("Unmapped IllegalArgumentException: {}", ex.getMessage());
            msg = "ข้อมูลไม่ถูกต้อง";
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(msg));
    }

    /** ไฟล์ใหญ่เกินกำหนด */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("ไฟล์มีขนาดใหญ่เกินกำหนด (สูงสุด 50MB)"));
    }

    /** RuntimeException จาก token/OTP flow — แปลง error code เป็น message ภาษาไทย */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        String msg = switch (ex.getMessage() != null ? ex.getMessage() : "") {
            case "INVALID_REFRESH_TOKEN"  -> "Token ไม่ถูกต้อง กรุณาเข้าสู่ระบบใหม่";
            case "REFRESH_TOKEN_EXPIRED"  -> "Token หมดอายุ กรุณาเข้าสู่ระบบใหม่";
            case "NO_REFRESH_TOKEN"       -> "ไม่พบ token กรุณาเข้าสู่ระบบใหม่";
            case "OTP_LOCKED"             -> "OTP ถูกล็อก กรุณาขอ OTP ใหม่";
            case "OTP_NOT_VERIFIED"       -> "กรุณายืนยัน OTP ก่อน";
            case "TOKEN_INVALID"          -> "Token ไม่ถูกต้อง";
            case "TOKEN_EXPIRED"          -> "Token หมดอายุ";
            case "ACCOUNT_DISABLED"       -> "บัญชีนี้ถูกระงับการใช้งาน";
            default                       -> null;
        };
        if (msg == null) {
            log.error("Unhandled RuntimeException", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("เกิดข้อผิดพลาดในระบบ"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(msg));
    }

    /** Catch-all — exception ที่ไม่ได้ handle ไว้โดยเฉพาะ */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("เกิดข้อผิดพลาดในระบบ"));
    }
}
