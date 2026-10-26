package com.breadShop.XXI.dto.customer;

import jakarta.validation.constraints.NotBlank;

public record UsernameRequest(
    @NotBlank(message = "username ห้ามว่าง")
    String username
) {}
