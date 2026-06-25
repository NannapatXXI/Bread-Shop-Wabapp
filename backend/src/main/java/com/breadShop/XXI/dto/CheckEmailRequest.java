package com.breadShop.XXI.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CheckEmailRequest(
    @NotBlank(message = "กรุณากรอก email")
    @Email(message = "รูปแบบ email ไม่ถูกต้อง")
    String email
) {}