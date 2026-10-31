package com.breadShop.XXI.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.customer.CustomerResponse;
import com.breadShop.XXI.dto.customer.UserOrderCount;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(userRepository, orderRepository);
    }

    private User customer(int id, String username, String role, boolean active, String provider) {
        User u = new User(username, username + "@example.com", "hashed", role);
        ReflectionTestUtils.setField(u, "id", id);
        u.setActive(active);
        u.setProvider(provider);
        return u;
    }

    private User stubBob() {
        User bob = customer(8, "bob", "USER", true, null);
        when(userRepository.findById(8)).thenReturn(Optional.of(bob));
        return bob;
    }

    private static HttpStatus statusOf(Throwable ex) {
        return HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
    }

    @Test
    void getAll_joinsOrderCounts_andDefaultsProviderToCredentials() {
        when(userRepository.findAll()).thenReturn(List.of(
                customer(7, "alice", "USER", true, null),
                customer(8, "bob", "USER", false, "google")));
        when(orderRepository.countOrdersGroupByUser()).thenReturn(List.of(new UserOrderCount(7, 3L)));

        List<CustomerResponse> result = customerService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
        assertThat(result.get(0).getProvider()).isEqualTo("credentials");
        assertThat(result.get(0).getOrderCount()).isEqualTo(3);
        assertThat(result.get(1).getProvider()).isEqualTo("google");
        assertThat(result.get(1).getOrderCount()).isZero();
        assertThat(result.get(1).getIsActive()).isFalse();
    }

    @Test
    void updateRole_normalizesCase_savesAndReturnsOldRole() {
        User bob = stubBob();

        String oldRole = customerService.updateRole(8, "admin");

        assertThat(oldRole).isEqualTo("USER");
        assertThat(bob.getRole()).isEqualTo("ADMIN");
        verify(userRepository).save(bob);
    }

    @Test
    void updateRole_invalidRole_throws400_beforeTouchingDb() {
        assertThatThrownBy(() -> customerService.updateRole(8, "SUPERADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("role ต้องเป็น USER หรือ ADMIN เท่านั้น");

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateRole_unknownUser_throws404() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateRole(999, "ADMIN"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(userRepository, never()).save(any());
    }

    @Test
    void setActive_setsRequestedState_andReturnsOldState() {
        User bob = stubBob();

        assertThat(customerService.setActive(8, false)).isTrue();
        assertThat(bob.isActive()).isFalse();

        // เรียกซ้ำด้วยค่าเดิม → ยังแบนอยู่ (ไม่สลับกลับเหมือน toggle)
        assertThat(customerService.setActive(8, false)).isFalse();
        assertThat(bob.isActive()).isFalse();

        assertThat(customerService.setActive(8, true)).isFalse();
        assertThat(bob.isActive()).isTrue();
    }

    @Test
    void updateUsername_trimsAndSaves() {
        User bob = stubBob();

        customerService.updateUsername(8, "  robert  ");

        assertThat(bob.getUsername()).isEqualTo("robert");
        verify(userRepository).save(bob);
    }

    @Test
    void updateUsername_blank_throws400_andDoesNotSave() {
        assertThatThrownBy(() -> customerService.updateUsername(8, "   "))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("username ห้ามว่าง");

        verifyNoInteractions(userRepository);
    }

    @Test
    void getByEmail_returnsTheLoggedInUser() {
        User bob = customer(8, "bob", "USER", true, null);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));

        assertThat(customerService.getByEmail("bob@example.com")).isSameAs(bob);
    }

    @Test
    void getByEmail_userDeletedButTokenStillValid_throws401() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getByEmail("ghost@example.com"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
