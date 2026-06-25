package com.breadShop.XXI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "กรุณากรอกรหัสผ่านใหม่")
    @Size(min = 8, message = "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร")
    String newPassword,

    @NotBlank(message = "ไม่พบ token")
    String token
) {}
