package com.breadShop.XXI.exception;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.breadShop.XXI.dto.ApiResponse;

/**
 * แปลง exception ทุกตัวเป็น ApiResponse { success:false, message } ที่ frontend อ่านได้
 *
 * service โยน error เป็น code (เช่น "EMAIL_EXISTS") แล้วที่นี่แปลเป็นภาษาไทย
 *   IllegalArgumentException + code → 400 (ข้อมูลจากผู้ใช้ผิด)
 *   RuntimeException + code         → 401 (token/session ใช้ไม่ได้)
 *   code ที่ไม่รู้จัก / exception อื่น → ข้อความกลาง ๆ — รายละเอียดเก็บใน log ฝั่ง server เท่านั้น
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String SERVER_ERROR = "เกิดข้อผิดพลาดในระบบ";

    private static final Map<String, String> BAD_REQUEST_MESSAGES = Map.of(
            "USER_NOT_FOUND",     "ไม่พบผู้ใช้ในระบบ",
            "USERNAME_EXISTS",    "มีชื่อผู้ใช้นี้ในระบบแล้ว",
            "EMAIL_EXISTS",       "มี Email นี้ในระบบแล้ว",
            "OTP_NOT_FOUND",      "OTP ไม่ถูกต้อง",
            "OTP_INVALID",        "OTP ไม่ถูกต้อง",
            "OTP_EXPIRED",        "OTP หมดเวลา",
            "OTP_LOCKED",         "OTP ถูกล็อก กรุณาขอ OTP ใหม่",
            "INVALID_IMAGE_TYPE", "รองรับเฉพาะไฟล์รูป jpg, png, webp, gif",
            "GOOGLE_ACCOUNT",     "GOOGLE_ACCOUNT");   // frontend เช็คค่านี้แล้วแนะนำให้กดปุ่ม Google

    private static final Map<String, String> UNAUTHORIZED_MESSAGES = Map.of(
            "INVALID_REFRESH_TOKEN", "Token ไม่ถูกต้อง กรุณาเข้าสู่ระบบใหม่",
            "REFRESH_TOKEN_EXPIRED", "Token หมดอายุ กรุณาเข้าสู่ระบบใหม่",
            "NO_REFRESH_TOKEN",      "ไม่พบ token กรุณาเข้าสู่ระบบใหม่",
            "OTP_NOT_VERIFIED",      "กรุณายืนยัน OTP ก่อน",
            "TOKEN_INVALID",         "Token ไม่ถูกต้อง",
            "TOKEN_EXPIRED",         "Token หมดอายุ",
            "ACCOUNT_DISABLED",      "บัญชีนี้ถูกระงับการใช้งาน");

    /** @Valid ไม่ผ่าน (JSON body หรือ form) — รวมข้อความทุก field */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    /** request ผิดรูปแบบ: ขาด param, ชนิดข้อมูลผิด, JSON อ่านไม่ได้ */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "ข้อมูลที่ส่งมาไม่ถูกต้อง");
    }

    /** path ที่ไม่มีอยู่ — 404 ไม่ใช่ 500 (ไม่งั้น log เต็มไปด้วย error ปลอม) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "ไม่พบหน้าที่ต้องการ");
    }

    /** รหัสผ่านผิด หรือไม่มีบัญชีนี้ — ข้อความเดียวกัน (เดาไม่ได้ว่าบัญชีมีอยู่ไหม) */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Username หรือ Password ไม่ถูกต้อง");
    }

    /** service กำหนด status + ข้อความเองแล้ว (404/403/400) */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        return error(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSize(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.BAD_REQUEST, "ไฟล์มีขนาดใหญ่เกินกำหนด (สูงสุด 50MB)");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = BAD_REQUEST_MESSAGES.get(Objects.toString(ex.getMessage(), ""));
        if (message == null) {
            // ข้อความที่ไม่รู้จักอาจมาจาก library (มีชื่อ class ภายใน) — ไม่ส่งให้ผู้ใช้
            log.warn("Unmapped IllegalArgumentException: {}", ex.getMessage());
            message = "ข้อมูลไม่ถูกต้อง";
        }
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        String message = UNAUTHORIZED_MESSAGES.get(Objects.toString(ex.getMessage(), ""));
        if (message == null) {
            log.error("Unhandled RuntimeException", ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, SERVER_ERROR);
        }
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, SERVER_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }
}
