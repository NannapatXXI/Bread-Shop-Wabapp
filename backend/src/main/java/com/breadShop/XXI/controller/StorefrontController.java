package com.breadShop.XXI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.storefront.StorefrontResponse;
import com.breadShop.XXI.service.StorefrontService;

/** /api/v1/storefront — รูปปก + สินค้าแนะนำของหน้าร้าน (สาธารณะ ไม่ต้อง login — หน้าแรกก็ใช้) */
@RestController
@RequestMapping("/api/v1/storefront")
public class StorefrontController {

    private final StorefrontService storefrontService;

    public StorefrontController(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StorefrontResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(storefrontService.getStorefront()));
    }
}
