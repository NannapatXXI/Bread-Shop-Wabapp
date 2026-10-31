package com.breadShop.XXI.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.breadShop.XXI.dto.notification.NotificationResponse;
import com.breadShop.XXI.service.NotificationService;
import com.breadShop.XXI.service.SseService;

@WebMvcTest(NotificationController.class)
@ExtendWith(OutputCaptureExtension.class)
class NotificationControllerTest extends ControllerTestSupport {

    private static final String ALICE = "alice@example.com";

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private SseService sseService;

    @Test
    void getAll_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void getAll_returnsNotificationsOfTokenUser() throws Exception {
        stubUser(7, ALICE, "USER");
        when(notificationService.getByUserId(7)).thenReturn(List.of(
                new NotificationResponse(1, 100, "ORD-100", "ออเดอร์จัดส่งแล้ว", "SHIPPED", false,
                        LocalDateTime.of(2026, 10, 1, 10, 0))));

        mockMvc.perform(get("/api/v1/notifications").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNo").value("ORD-100"))
                .andExpect(jsonPath("$.data[0].newStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.data[0].isRead").value(false));

        verify(notificationService).getByUserId(7);
    }

    @Test
    void getAll_tokenUserMissingFromDb_returns401_likeEveryOtherEndpoint() throws Exception {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/notifications").cookie(userToken("ghost@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void markAsRead_passesNotificationId() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(patch("/api/v1/notifications/42/read").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("อ่านแล้ว"));

        verify(notificationService).markAsRead(42, 7);
    }

    @Test
    void markAsRead_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/42/read"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void markAllAsRead_usesTokenUserId() throws Exception {
        stubUser(7, ALICE, "USER");

        mockMvc.perform(patch("/api/v1/notifications/read-all").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("อ่านทั้งหมดแล้ว"));

        verify(notificationService).markAllAsRead(7);
    }

    @Test
    void stream_subscribesTokenUser_andStartsAsyncResponse() throws Exception {
        stubUser(7, ALICE, "USER");
        when(sseService.subscribe(7)).thenReturn(Optional.of(new SseEmitter(0L)));

        mockMvc.perform(get("/api/v1/notifications/stream").cookie(userToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseService).subscribe(7);
    }

    @Test
    void stream_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sseService);
    }

    @Test
    void stream_overTheTabLimit_returns429_soTheBrowserStopsAndBacksOff(CapturedOutput output) throws Exception {
        stubUser(7, ALICE, "USER");
        when(sseService.subscribe(7)).thenReturn(Optional.empty());

        // EventSource ส่ง Accept: text/event-stream — ต้องได้ 429 ตรง ๆ (ไม่ใช่ 406/500)
        mockMvc.perform(get("/api/v1/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM)
                        .cookie(userToken(ALICE)))
                .andExpect(status().isTooManyRequests());

        // แท็บที่เกินจะลองใหม่เรื่อย ๆ — ต้องไม่ทิ้ง WARN + stack trace ไว้ใน log ทุกครั้ง
        assertThat(output).doesNotContain("Failure in @ExceptionHandler").doesNotContain("HttpMediaTypeNotAcceptableException");
    }
}
