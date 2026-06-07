package com.breadShop.XXI.service;

import java.util.List;
import java.util.stream.Collectors;

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

@Service
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    public UserAddressService(UserAddressRepository userAddressRepository,
                               UserRepository userRepository) {
        this.userAddressRepository = userAddressRepository;
        this.userRepository = userRepository;
    }

    /**
     * ดึงที่อยู่ทั้งหมดของ user คนนี้ 
     * @param userId ID ของ user ที่ต้องการดึงที่อยู่
     * @return รายการที่อยู่ทั้งหมดของ user คนนี้ในรูปแบบ List<UserAddressResponse> ซึ่งเป็น DTO ที่มีข้อมูลที่จำเป็นสำหรับ client เท่านั้น โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ
     */
    public List<UserAddressResponse> getAddressesByUserId(Integer userId) {
        return userAddressRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // เพิ่มที่อยู่ใหม่
    @Transactional
    public UserAddressResponse createAddress(Integer userId, UserAddressRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // ถ้าตั้งเป็น default → เอา default เก่าออกก่อน
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetCurrentDefault(userId);
        }

        // ถ้ายังไม่มีที่อยู่เลย → บังคับเป็น default
        if (!userAddressRepository.existsByUserIdAndIsDefaultTrue(userId)) {
            request.setIsDefault(true);
        }

        UserAddress address = new UserAddress(
                user,
                request.getName(),
                request.getRecipientName(),
                request.getPhone(),
                request.getAddress(),
                request.getProvince(),
                request.getDistrict(),
                request.getSubdistrict(),
                request.getPostcode(),
                request.getIsDefault()
        );

        return toResponse(userAddressRepository.save(address));
    }

    // แก้ไขที่อยู่
    @Transactional
    public UserAddressResponse updateAddress(Integer addressId, UserAddressRequest request) {

        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Address not found"));

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetCurrentDefault(address.getUser().getId());
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

    // ลบที่อยู่
    public void deleteAddress(Integer addressId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Address not found"));
        userAddressRepository.delete(address);
    }

    // เซ็ต default
    @Transactional
    public UserAddressResponse setDefault(Integer userId, Integer addressId) {
        unsetCurrentDefault(userId);

        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Address not found"));

        address.setIsDefault(true);
        return toResponse(userAddressRepository.save(address));
    }

    // helper — เอา default เก่าออก
    private void unsetCurrentDefault(Integer userId) {
        userAddressRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(a -> {
                    a.setIsDefault(false);
                    userAddressRepository.save(a);
                });
    }

    // helper — แปลง Entity → Response
    /**
     * จำกัดข้อมูลที่ส่งกลับให้เฉพาะข้อมูลที่จำเป็นสำหรับ client โดยไม่เปิดเผยข้อมูลภายในหรือข้อมูลที่ไม่จำเป็น 
     * @param a UserAddress Entity ที่ต้องการแปลงเป็น Response DTO
     * @return UserAddressResponse DTO ที่มีข้อมูลที่จำเป็นสำหรับ client เท่านั้น โดยไม่รวมข้อมูลภายในหรือข้อมูลที่ไม่จำเป็นอื่นๆ
     */
    private UserAddressResponse toResponse(UserAddress a) {
        return new UserAddressResponse(
                a.getId(), a.getName(), a.getRecipientName(),
                a.getPhone(), a.getAddress(), a.getProvince(),
                a.getDistrict(), a.getSubdistrict(),
                a.getPostcode(), a.getIsDefault()
        );
    }
}