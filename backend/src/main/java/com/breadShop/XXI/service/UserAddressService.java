package com.breadShop.XXI.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.useraddress.UserAddressRequest;
import com.breadShop.XXI.dto.useraddress.UserAddressResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;

/**
 * ที่อยู่จัดส่งของลูกค้า — 1 คนมี default ได้ที่เดียว
 * ทุก method ที่แตะที่อยู่เดิมเช็คเจ้าของเสมอ: ที่อยู่ของคนอื่นตอบ 404 เหมือนไม่มีอยู่
 */
@Service
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserRepository        userRepository;

    public UserAddressService(UserAddressRepository userAddressRepository, UserRepository userRepository) {
        this.userAddressRepository = userAddressRepository;
        this.userRepository        = userRepository;
    }

    public List<UserAddressResponse> getAddressesByUserId(Integer userId) {
        return userAddressRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    /** ที่อยู่แรกของ user เป็น default เสมอ */
    @Transactional
    public UserAddressResponse createAddress(Integer userId, UserAddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetCurrentDefault(userId);
        }
        if (!userAddressRepository.existsByUserIdAndIsDefaultTrue(userId)) {
            request.setIsDefault(true);
        }

        UserAddress address = new UserAddress(user, request.getName(), request.getRecipientName(), request.getPhone(),
                request.getAddress(), request.getProvince(), request.getDistrict(), request.getSubdistrict(),
                request.getPostcode(), request.getIsDefault());
        return toResponse(userAddressRepository.save(address));
    }

    @Transactional
    public UserAddressResponse updateAddress(Integer userId, Integer addressId, UserAddressRequest request) {
        UserAddress address = findOwnedAddress(userId, addressId);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetCurrentDefault(userId);
        }

        address.setName(request.getName());
        address.setRecipientName(request.getRecipientName());
        address.setPhone(request.getPhone());
        address.setAddress(request.getAddress());
        address.setProvince(request.getProvince());
        address.setDistrict(request.getDistrict());
        address.setSubdistrict(request.getSubdistrict());
        address.setPostcode(request.getPostcode());
        address.setIsDefault(request.getIsDefault());
        return toResponse(userAddressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Integer userId, Integer addressId) {
        userAddressRepository.delete(findOwnedAddress(userId, addressId));
    }

    @Transactional
    public UserAddressResponse setDefault(Integer userId, Integer addressId) {
        // เช็คเจ้าของก่อน — ถ้า unset ก่อนแล้วค่อยพบว่าไม่ใช่ของเรา default เดิมจะหายไปฟรี ๆ
        UserAddress address = findOwnedAddress(userId, addressId);
        unsetCurrentDefault(userId);
        address.setIsDefault(true);
        return toResponse(userAddressRepository.save(address));
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private UserAddress findOwnedAddress(Integer userId, Integer addressId) {
        return userAddressRepository.findById(addressId)
                .filter(a -> a.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void unsetCurrentDefault(Integer userId) {
        userAddressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(a -> {
            a.setIsDefault(false);
            userAddressRepository.save(a);
        });
    }

    private UserAddressResponse toResponse(UserAddress a) {
        return new UserAddressResponse(a.getId(), a.getName(), a.getRecipientName(), a.getPhone(), a.getAddress(),
                a.getProvince(), a.getDistrict(), a.getSubdistrict(), a.getPostcode(), a.getIsDefault());
    }
}
