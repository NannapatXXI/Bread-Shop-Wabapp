package com.breadShop.XXI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
    @NotBlank(message = "ไม่พบ token")
    String token,

    @NotBlank(message = "กรุณากรอก OTP")
    @Pattern(regexp = "\\d{6}", message = "OTP ต้องเป็นตัวเลข 6 หลัก")
    String otp
) {}
    
