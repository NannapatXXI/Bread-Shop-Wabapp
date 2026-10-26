package com.breadShop.XXI.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.useraddress.UserAddressRequest;
import com.breadShop.XXI.dto.useraddress.UserAddressResponse;
import com.breadShop.XXI.service.UserAddressService;

/** /api/v1/users/me/addresses — ที่อยู่ของคนที่ login อยู่ (userId มาจาก token ไม่ต้องส่งมาและปลอมไม่ได้) */
@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class UserAddressController {

    private final UserAddressService userAddressService;
    private final CurrentUser        currentUser;

    public UserAddressController(UserAddressService userAddressService, CurrentUser currentUser) {
        this.userAddressService = userAddressService;
        this.currentUser        = currentUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAddressResponse>>> getAll(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(userAddressService.getAddressesByUserId(userId(email))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserAddressResponse>> create(
            @AuthenticationPrincipal String email, @RequestBody UserAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("เพิ่มที่อยู่สำเร็จ", userAddressService.createAddress(userId(email), request)));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> update(
            @PathVariable Integer addressId, @AuthenticationPrincipal String email,
            @RequestBody UserAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("แก้ไขที่อยู่สำเร็จ",
                userAddressService.updateAddress(userId(email), addressId, request)));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer addressId, @AuthenticationPrincipal String email) {
        userAddressService.deleteAddress(userId(email), addressId);
        return ResponseEntity.ok(ApiResponse.ok("ลบที่อยู่สำเร็จ"));
    }

    /** ตั้งเป็นที่อยู่หลัก — ที่อยู่หลักเดิมถูกยกเลิกให้อัตโนมัติ */
    @PutMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<UserAddressResponse>> setDefault(
            @PathVariable Integer addressId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok("ตั้ง default สำเร็จ", userAddressService.setDefault(userId(email), addressId)));
    }

    private Integer userId(String email) {
        return currentUser.get(email).getId();
    }
}
