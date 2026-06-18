package com.breadShop.XXI.dto;


public record AuthenticationRequest(
        String email,
        String password
) {}
