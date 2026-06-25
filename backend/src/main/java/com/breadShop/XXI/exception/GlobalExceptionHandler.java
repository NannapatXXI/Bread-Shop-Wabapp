package com.breadShop.XXI.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

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

    /** @Valid validation ล้มเหลว — รวม error ทุก field เป็น message เดียว */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg));
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
        String msg = switch (ex.getMessage()) {
            case "USER_NOT_FOUND"    -> "ไม่พบผู้ใช้ในระบบ";
            case "EMAIL_NOT_FOUND"   -> "ไม่พบ Email ในระบบ";
            case "USERNAME_EXISTS"   -> "มีชื่อผู้ใช้นี้ในระบบแล้ว";
            case "EMAIL_EXISTS"      -> "มี Email นี้ในระบบแล้ว";
            case "OTP_NOT_FOUND"     -> "OTP ไม่ถูกต้อง";
            case "OTP_EXPIRED"       -> "OTP หมดเวลา";
            case "OTP_INVALID"       -> "OTP ไม่ถูกต้อง";
            case "Product not found" -> "ไม่พบสินค้า";
            default                  -> "ข้อมูลไม่ถูกต้อง: " + ex.getMessage();
        };
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
            default                       -> null;
        };
        if (msg == null) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("เกิดข้อผิดพลาดในระบบ"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(msg));
    }

    /** Catch-all — exception ที่ไม่ได้ handle ไว้โดยเฉพาะ */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        ex.printStackTrace(); // log เพื่อ debug ใน console
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("เกิดข้อผิดพลาดในระบบ"));
    }
}
