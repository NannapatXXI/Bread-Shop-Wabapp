package com.breadShop.XXI.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.useraddress.UserAddressRequest;
import com.breadShop.XXI.dto.useraddress.UserAddressResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private UserRepository userRepository;

    private UserAddressService userAddressService;

    @BeforeEach
    void setUp() {
        userAddressService = new UserAddressService(userAddressRepository, userRepository);
    }

    private User sampleUser(int id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserAddressRequest sampleRequest(boolean isDefault) {
        UserAddressRequest req = new UserAddressRequest();
        req.setName("Home");
        req.setRecipientName("Peak");
        req.setPhone("0800000000");
        req.setAddress("123 Bakery Rd");
        req.setProvince("Bangkok");
        req.setDistrict("Bang Rak");
        req.setSubdistrict("Silom");
        req.setPostcode("10500");
        req.setIsDefault(isDefault);
        return req;
    }

    @Test
    void getAddressesByUserId_shouldReturnMappedList() {
        UserAddress address = new UserAddress(sampleUser(1), "Home", "Peak", "0800000000",
                "123 Bakery Rd", "Bangkok", "Bang Rak", "Silom", "10500", true);
        when(userAddressRepository.findByUserId(1)).thenReturn(List.of(address));

        List<UserAddressResponse> result = userAddressService.getAddressesByUserId(1);

        assertThat(result).hasSize(1);
        assertEquals("Home", result.get(0).getName());
    }

    @Test
    void createAddress_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(anyInt())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> userAddressService.createAddress(1, sampleRequest(false)));
    }

    @Test
    void createAddress_whenRequestIsDefaultTrue_shouldUnsetOldDefaultFirst() {
        User user = sampleUser(1);
        UserAddress oldDefault = new UserAddress(user, "Old", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", true);

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(1)).thenReturn(Optional.of(oldDefault));
        when(userAddressRepository.existsByUserIdAndIsDefaultTrue(1)).thenReturn(true);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        userAddressService.createAddress(1, sampleRequest(true));

        assertEquals(false, oldDefault.getIsDefault());
        verify(userAddressRepository, times(2)).save(any(UserAddress.class));
    }

    @Test
    void createAddress_whenNoExistingDefault_shouldForceIsDefaultTrue() {
        User user = sampleUser(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userAddressRepository.existsByUserIdAndIsDefaultTrue(1)).thenReturn(false);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddressRequest request = sampleRequest(false);
        UserAddressResponse response = userAddressService.createAddress(1, request);

        assertEquals(true, response.getIsDefault());
    }

    @Test
    void updateAddress_whenNotFound_shouldThrowNotFound() {
        when(userAddressRepository.findById(anyInt())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> userAddressService.updateAddress(1, 99, sampleRequest(false)));
    }

    @Test
    void updateAddress_whenSetDefaultTrue_shouldUnsetOldDefaultAndUpdateFields() {
        User user = sampleUser(1);
        UserAddress existing = new UserAddress(user, "Old", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", false);
        UserAddress oldDefault = new UserAddress(user, "Other", "Peak", "080", "addr2",
                "Bangkok", "Bang Rak", "Silom", "10600", true);

        when(userAddressRepository.findById(5)).thenReturn(Optional.of(existing));
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(1)).thenReturn(Optional.of(oldDefault));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddressRequest request = sampleRequest(true);
        UserAddressResponse response = userAddressService.updateAddress(1, 5, request);

        assertEquals(false, oldDefault.getIsDefault());
        assertEquals("Home", response.getName());
    }

    @Test
    void deleteAddress_whenFound_shouldDelete() {
        UserAddress address = new UserAddress(sampleUser(1), "Home", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", true);
        when(userAddressRepository.findById(5)).thenReturn(Optional.of(address));

        userAddressService.deleteAddress(1, 5);

        verify(userAddressRepository).delete(address);
    }

    @Test
    void updateAddress_someoneElsesAddress_shouldThrowNotFound_andChangeNothing() {
        UserAddress alices = new UserAddress(sampleUser(1), "Home", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", false);
        when(userAddressRepository.findById(5)).thenReturn(Optional.of(alices));

        assertThrows(ResponseStatusException.class,
                () -> userAddressService.updateAddress(2, 5, sampleRequest(true)));

        assertEquals("Home", alices.getName());
        assertEquals("addr", alices.getAddress());
        verify(userAddressRepository, never()).save(any());
    }

    @Test
    void deleteAddress_someoneElsesAddress_shouldThrowNotFound_andNotDelete() {
        UserAddress alices = new UserAddress(sampleUser(1), "Home", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", true);
        when(userAddressRepository.findById(5)).thenReturn(Optional.of(alices));

        assertThrows(ResponseStatusException.class, () -> userAddressService.deleteAddress(2, 5));

        verify(userAddressRepository, never()).delete(any());
    }

    @Test
    void setDefault_someoneElsesAddress_shouldThrow_andKeepCallersCurrentDefault() {
        UserAddress alices = new UserAddress(sampleUser(1), "Home", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", false);
        when(userAddressRepository.findById(5)).thenReturn(Optional.of(alices));

        assertThrows(ResponseStatusException.class, () -> userAddressService.setDefault(2, 5));

        assertEquals(false, alices.getIsDefault());
        verify(userAddressRepository, never()).findByUserIdAndIsDefaultTrue(anyInt());
        verify(userAddressRepository, never()).save(any());
    }

    @Test
    void deleteAddress_whenNotFound_shouldThrowNotFound() {
        when(userAddressRepository.findById(anyInt())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userAddressService.deleteAddress(1, 99));
        verify(userAddressRepository, never()).delete(any());
    }

    @Test
    void setDefault_shouldUnsetOldAndSetNewDefault() {
        User user = sampleUser(1);
        UserAddress oldDefault = new UserAddress(user, "Old", "Peak", "080", "addr",
                "Bangkok", "Bang Rak", "Silom", "10500", true);
        UserAddress target = new UserAddress(user, "New", "Peak", "080", "addr2",
                "Bangkok", "Bang Rak", "Silom", "10600", false);

        when(userAddressRepository.findByUserIdAndIsDefaultTrue(1)).thenReturn(Optional.of(oldDefault));
        when(userAddressRepository.findById(5)).thenReturn(Optional.of(target));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddressResponse response = userAddressService.setDefault(1, 5);

        assertEquals(false, oldDefault.getIsDefault());
        assertEquals(true, response.getIsDefault());
    }

    @Test
    void setDefault_whenAddressNotFound_shouldThrowNotFound() {
        when(userAddressRepository.findById(anyInt())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userAddressService.setDefault(1, 99));
    }
}
