package com.breadShop.XXI.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "กรุณากรอก username")
    @Size(min = 3, max = 50, message = "username ต้องมี 3-50 ตัวอักษร")
    String username,

    @NotBlank(message = "กรุณากรอก email")
    @Email(message = "รูปแบบ email ไม่ถูกต้อง")
    String email,

    @NotBlank(message = "กรุณากรอกรหัสผ่าน")
    @Size(min = 8, message = "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร")
    String password
) {}