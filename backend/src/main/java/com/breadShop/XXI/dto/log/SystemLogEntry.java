package com.breadShop.XXI.dto.log;

public record SystemLogEntry(
    String timestamp,
    String thread,
    String level,
    String logger,
    String message
) {}
