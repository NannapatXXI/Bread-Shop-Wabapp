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
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.useraddress.UserAddressRequest;
import com.breadShop.XXI.dto.useraddress.UserAddressResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.UserAddressService;

/**
 * [Claude] UserAddressController — CRUD ที่อยู่จัดส่งของ user
 * path: /api/users/{userId}/addresses/**
 *
 * ทุก endpoint ตรวจว่า {userId} ตรงกับ user ที่ login อยู่ (ownership check)
 */
@RestController
@RequestMapping("/api/users/{userId}/addresses")
public class UserAddressController {

    private final UserAddressService userAddressService;
    private final UserRepository userRepository;

    public UserAddressController(UserAddressService userAddressService,
                                  UserRepository userRepository) {
        this.userAddressService = userAddressService;
        this.userRepository = userRepository;
    }

    private void assertOwner(String email, Integer userId) {
        User authUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!authUser.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เข้าถึงข้อมูลของ user คนอื่น");
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAddressResponse>>> getAll(
            @PathVariable Integer userId,
            @AuthenticationPrincipal String email) {
        assertOwner(email, userId);
        return ResponseEntity.ok(ApiResponse.ok(userAddressService.getAddressesByUserId(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserAddressResponse>> create(
            @PathVariable Integer userId,
            @AuthenticationPrincipal String email,
            @RequestBody UserAddressRequest request) {
        assertOwner(email, userId);
        return ResponseEntity.ok(ApiResponse.ok("เพิ่มที่อยู่สำเร็จ",
                userAddressService.createAddress(userId, request)));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> update(
            @PathVariable Integer userId,
            @PathVariable Integer addressId,
            @AuthenticationPrincipal String email,
            @RequestBody UserAddressRequest request) {
        assertOwner(email, userId);
        return ResponseEntity.ok(ApiResponse.ok("แก้ไขที่อยู่สำเร็จ",
                userAddressService.updateAddress(addressId, request)));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer userId,
            @PathVariable Integer addressId,
            @AuthenticationPrincipal String email) {
        assertOwner(email, userId);
        userAddressService.deleteAddress(addressId);
        return ResponseEntity.ok(ApiResponse.ok("ลบที่อยู่สำเร็จ"));
    }

    @PutMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<UserAddressResponse>> setDefault(
            @PathVariable Integer userId,
            @PathVariable Integer addressId,
            @AuthenticationPrincipal String email) {
        assertOwner(email, userId);
        return ResponseEntity.ok(ApiResponse.ok("ตั้ง default สำเร็จ",
                userAddressService.setDefault(userId, addressId)));
    }
}
