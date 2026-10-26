package com.breadShop.XXI.dto.customer;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
    @NotBlank(message = "role ต้องเป็น USER หรือ ADMIN เท่านั้น")
    String role
) {}
