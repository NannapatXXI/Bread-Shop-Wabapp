package com.breadShop.XXI.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.breadShop.XXI.dto.useraddress.UserAddressRequest;
import com.breadShop.XXI.dto.useraddress.UserAddressResponse;
import com.breadShop.XXI.service.UserAddressService;

@WebMvcTest(UserAddressController.class)
class UserAddressControllerTest extends ControllerTestSupport {

    private static final String ALICE = "alice@example.com";
    private static final String BODY = """
            { "name": "บ้าน", "recipientName": "Alice", "phone": "0800000000",
              "address": "1 Road", "province": "Bangkok", "district": "Pathumwan",
              "subdistrict": "Lumphini", "postcode": "10330", "isDefault": true }
            """;

    @MockitoBean
    private UserAddressService userAddressService;

    private UserAddressResponse address(int id, boolean isDefault) {
        return new UserAddressResponse(id, "บ้าน", "Alice", "0800000000", "1 Road",
                "Bangkok", "Pathumwan", "Lumphini", "10330", isDefault);
    }

    @Test
    void getAll_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/7/addresses"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAddressService);
    }

    @Test
    void getAll_ownAddresses_returnsList() throws Exception {
        stubUser(7, ALICE, "USER");
        when(userAddressService.getAddressesByUserId(7)).thenReturn(List.of(address(1, true), address(2, false)));

        mockMvc.perform(get("/api/users/7/addresses").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].postcode").value("10330"));
    }

    @Test
    void getAll_anotherUsersId_returns403_andNeverLoadsTheirAddresses() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(get("/api/users/8/addresses").cookie(userToken(ALICE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ไม่มีสิทธิ์เข้าถึงข้อมูลของ user คนอื่น"));

        verifyNoInteractions(userAddressService);
    }

    @Test
    void create_bindsEveryFieldAndUsesPathUserId() throws Exception {
        stubUser(7, ALICE, "USER");
        when(userAddressService.createAddress(eq(7), any())).thenReturn(address(3, true));

        mockMvc.perform(post("/api/users/7/addresses").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("เพิ่มที่อยู่สำเร็จ"))
                .andExpect(jsonPath("$.data.id").value(3));

        ArgumentCaptor<UserAddressRequest> captor = ArgumentCaptor.forClass(UserAddressRequest.class);
        verify(userAddressService).createAddress(eq(7), captor.capture());
        UserAddressRequest sent = captor.getValue();
        assertThat(sent.getName()).isEqualTo("บ้าน");
        assertThat(sent.getRecipientName()).isEqualTo("Alice");
        assertThat(sent.getPhone()).isEqualTo("0800000000");
        assertThat(sent.getPostcode()).isEqualTo("10330");
        assertThat(sent.getIsDefault()).isTrue();
    }

    @Test
    void create_forAnotherUser_returns403() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(post("/api/users/8/addresses").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAddressService);
    }

    @Test
    void update_passesAddressIdToService() throws Exception {
        stubUser(7, ALICE, "USER");
        when(userAddressService.updateAddress(eq(7), eq(3), any())).thenReturn(address(3, true));

        mockMvc.perform(put("/api/users/7/addresses/3").cookie(userToken(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("แก้ไขที่อยู่สำเร็จ"));

        verify(userAddressService).updateAddress(eq(7), eq(3), any());
    }

    @Test
    void delete_ownAddress_callsService() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(delete("/api/users/7/addresses/3").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ลบที่อยู่สำเร็จ"));

        verify(userAddressService).deleteAddress(7, 3);
    }

    @Test
    void delete_viaAnotherUsersPath_returns403() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(delete("/api/users/8/addresses/3").cookie(userToken(ALICE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAddressService);
    }

    @Test
    void setDefault_passesUserAndAddressIds() throws Exception {
        stubUser(7, ALICE, "USER");
        when(userAddressService.setDefault(7, 3)).thenReturn(address(3, true));

        mockMvc.perform(put("/api/users/7/addresses/3/default").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        verify(userAddressService).setDefault(7, 3);
    }
}
