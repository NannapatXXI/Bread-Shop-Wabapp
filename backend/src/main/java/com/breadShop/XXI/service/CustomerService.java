package com.breadShop.XXI.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.customer.CustomerResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.UserRepository;

/** หน้าจัดการลูกค้าของ admin — ดูรายชื่อ, เปลี่ยน role, แบน, แก้ username */
@Service
public class CustomerService {

    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    private final UserRepository  userRepository;
    private final OrderRepository orderRepository;

    public CustomerService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository  = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {
        // นับ order ของทุกคนใน query เดียว (ไม่ยิง query แยกต่อคน)
        Map<Integer, Long> orderCount = orderRepository.countOrdersGroupByUser().stream()
                .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Long) row[1]));

        return userRepository.findAll().stream()
                .map(user -> new CustomerResponse(
                        user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                        user.getProvider() != null ? user.getProvider() : "credentials",
                        user.getCreatedAt(),
                        orderCount.getOrDefault(user.getId(), 0L).intValue(),
                        user.isActive()))
                .toList();
    }

    /** @return role เดิม (ไว้ลง audit log) */
    @Transactional
    public String updateRole(Integer id, String role) {
        String newRole = role.toUpperCase(Locale.ROOT);
        if (!ROLES.contains(newRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role ต้องเป็น USER หรือ ADMIN เท่านั้น");
        }
        User user = findUser(id);
        String oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        return oldRole;
    }

    /** แบน (false) / ปลดแบน (true) @return สถานะเดิม (ไว้ลง audit log) */
    @Transactional
    public boolean setActive(Integer id, boolean active) {
        User user = findUser(id);
        boolean wasActive = user.isActive();
        user.setActive(active);
        userRepository.save(user);
        return wasActive;
    }

    @Transactional
    public void updateUsername(Integer id, String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username ห้ามว่าง");
        }
        User user = findUser(id);
        user.setUsername(username.trim());
        userRepository.save(user);
    }

    private User findUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบผู้ใช้"));
    }
}
