package com.breadShop.XXI.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;

/**
 * แปลง email ใน token (@AuthenticationPrincipal) เป็น User — ใช้ร่วมกันทุก controller
 * token ยังไม่หมดอายุแต่ user ถูกลบไปแล้ว → 401 ให้ frontend พาไป login ใหม่
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
