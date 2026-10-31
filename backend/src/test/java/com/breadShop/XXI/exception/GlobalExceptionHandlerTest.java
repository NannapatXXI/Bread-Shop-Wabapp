package com.breadShop.XXI.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertError(ResponseEntity<ApiResponse<Void>> res, HttpStatus status, String message) {
        assertThat(res.getStatusCode()).isEqualTo(status);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().isSuccess()).isFalse();
        assertThat(res.getBody().getData()).isNull();
        assertThat(res.getBody().getMessage()).isEqualTo(message);
    }

    @Test
    void validationErrors_joinEveryFieldMessage_with400() throws Exception {
        record Form(String a, String b) {}
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Form(null, null), "form");
        binding.rejectValue("a", "NotBlank", "กรุณากรอก a");
        binding.rejectValue("b", "NotBlank", "กรุณากรอก b");
        Method m = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationErrors_joinEveryFieldMessage_with400");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(new MethodParameter(m, -1), binding);

        assertError(handler.handleValidation(ex), HttpStatus.BAD_REQUEST, "กรุณากรอก a, กรุณากรอก b");
    }

    @Test
    void badCredentials_is401_withGenericMessage() {
        assertError(handler.handleBadCredentials(new BadCredentialsException("x")),
                HttpStatus.UNAUTHORIZED, "Username หรือ Password ไม่ถูกต้อง");
    }

    @Test
    void responseStatusException_keepsItsStatusAndReason() {
        assertError(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.CONFLICT, "ซ้ำ")),
                HttpStatus.CONFLICT, "ซ้ำ");
    }

    @ParameterizedTest
    @CsvSource({
        "USER_NOT_FOUND,    ไม่พบผู้ใช้ในระบบ",
        "USERNAME_EXISTS,   มีชื่อผู้ใช้นี้ในระบบแล้ว",
        "EMAIL_EXISTS,      มี Email นี้ในระบบแล้ว",
        "OTP_NOT_FOUND,     OTP ไม่ถูกต้อง",
        "OTP_EXPIRED,       OTP หมดเวลา",
        "OTP_INVALID,       OTP ไม่ถูกต้อง",
    })
    void illegalArgument_knownCodes_areTranslated(String code, String thai) {
        assertError(handler.handleIllegalArgument(new IllegalArgumentException(code)), HttpStatus.BAD_REQUEST, thai);
    }

    @Test
    void illegalArgument_unknownMessage_isNotEchoedToTheClient() {
        // ข้อความจาก library/JDK มีชื่อ class ภายใน — ห้ามส่งออกไปหน้าเว็บ
        var res = handler.handleIllegalArgument(new IllegalArgumentException(
                "No enum constant com.breadShop.XXI.entity.ProductCategory.PIZZA"));

        assertError(res, HttpStatus.BAD_REQUEST, "ข้อมูลไม่ถูกต้อง");
    }

    @Test
    void unknownPath_is404_notServerError() {
        var res = handler.handleNotFound(new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/does-not-exist", "does-not-exist"));

        assertError(res, HttpStatus.NOT_FOUND, "ไม่พบหน้าที่ต้องการ");
    }

    @Test
    void illegalArgument_otpLocked_hasThaiMessage() {
        assertError(handler.handleIllegalArgument(new IllegalArgumentException("OTP_LOCKED")),
                HttpStatus.BAD_REQUEST, "OTP ถูกล็อก กรุณาขอ OTP ใหม่");
    }

    @Test
    void illegalArgument_withoutMessage_stillReturns400_insteadOfCrashing() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void maxUploadSize_is400_withThaiMessage() {
        assertError(handler.handleMaxSize(new MaxUploadSizeExceededException(50L * 1024 * 1024)),
                HttpStatus.BAD_REQUEST, "ไฟล์มีขนาดใหญ่เกินกำหนด (สูงสุด 50MB)");
    }

    @ParameterizedTest
    @CsvSource({
        "INVALID_REFRESH_TOKEN, Token ไม่ถูกต้อง กรุณาเข้าสู่ระบบใหม่",
        "REFRESH_TOKEN_EXPIRED, Token หมดอายุ กรุณาเข้าสู่ระบบใหม่",
        "NO_REFRESH_TOKEN,      ไม่พบ token กรุณาเข้าสู่ระบบใหม่",
        "OTP_NOT_VERIFIED,      กรุณายืนยัน OTP ก่อน",
        "TOKEN_INVALID,         Token ไม่ถูกต้อง",
        "TOKEN_EXPIRED,         Token หมดอายุ",
    })
    void runtime_authCodes_are401(String code, String thai) {
        assertError(handler.handleRuntime(new RuntimeException(code)), HttpStatus.UNAUTHORIZED, thai);
    }

    @Test
    void runtime_unknownOrNullMessage_is500_withoutLeakingDetails() {
        assertError(handler.handleRuntime(new RuntimeException("SQL syntax error near users")),
                HttpStatus.INTERNAL_SERVER_ERROR, "เกิดข้อผิดพลาดในระบบ");
        assertError(handler.handleRuntime(new RuntimeException()),
                HttpStatus.INTERNAL_SERVER_ERROR, "เกิดข้อผิดพลาดในระบบ");
    }

    @Test
    void checkedException_is500_withGenericMessage() {
        assertError(handler.handleOther(new Exception("disk full")),
                HttpStatus.INTERNAL_SERVER_ERROR, "เกิดข้อผิดพลาดในระบบ");
    }
}
