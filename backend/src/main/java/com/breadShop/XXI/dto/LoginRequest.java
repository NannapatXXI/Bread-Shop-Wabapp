package com.breadShop.XXI.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "กรุณากรอก email หรือ username") String usernameOrEmail,
    @NotBlank(message = "กรุณากรอกรหัสผ่าน") String password
) {}