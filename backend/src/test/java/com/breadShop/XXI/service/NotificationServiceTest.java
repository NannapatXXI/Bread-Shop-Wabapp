package com.breadShop.XXI.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.breadShop.XXI.dto.notification.NotificationResponse;
import com.breadShop.XXI.entity.Notification;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.NotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseService sseService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, sseService);
    }

    private Order sampleOrder(int userId) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 100);
        ReflectionTestUtils.setField(order, "orderNo", "ORD-100");
        ReflectionTestUtils.setField(order, "user", user);
        return order;
    }

    @Test
    void createAndPush_withConfirmedStatus_shouldSaveAndPushExpectedMessage() {
        Order order = sampleOrder(1);

        notificationService.createAndPush(order, OrderStatus.CONFIRMED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("ORD-100").contains("ยืนยันแล้ว");

        verify(sseService).send(eq(1), any(NotificationResponse.class));
    }

    @Test
    void createAndPush_withPendingStatus_shouldUseDefaultMessage() {
        Order order = sampleOrder(1);

        notificationService.createAndPush(order, OrderStatus.PENDING);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("มีการอัปเดตสถานะ");
    }

    @Test
    void getByUserId_shouldReturnMappedList() {
        Order order = sampleOrder(1);
        Notification notification = new Notification(order.getUser(), order, "msg", "CONFIRMED");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1)).thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.getByUserId(1);

        assertThat(result).hasSize(1);
        assertEquals("msg", result.get(0).message());
    }

    @Test
    void markAsRead_whenFound_shouldSetReadAndSave() {
        Order order = sampleOrder(1);
        Notification notification = new Notification(order.getUser(), order, "msg", "CONFIRMED");
        when(notificationRepository.findById(5)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(5, 1);

        assertEquals(true, notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_someoneElsesNotification_isLeftUnread() {
        Order order = sampleOrder(1);
        Notification notification = new Notification(order.getUser(), order, "msg", "CONFIRMED");
        when(notificationRepository.findById(5)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(5, 2);

        assertEquals(false, notification.isRead());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_whenNotFound_shouldDoNothing() {
        when(notificationRepository.findById(anyInt())).thenReturn(Optional.empty());

        notificationService.markAsRead(999, 1);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsRead_shouldDelegateToRepository() {
        notificationService.markAllAsRead(1);

        verify(notificationRepository).markAllReadByUserId(1);
    }

    @Test
    void createAndPush_withProcessingStatus_shouldUseProcessingMessage() {
        Order order = sampleOrder(1);
        notificationService.createAndPush(order, OrderStatus.PROCESSING);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("กำลังเตรียมสินค้า");
    }

    @Test
    void createAndPush_withShippedStatus_shouldUseShippedMessage() {
        Order order = sampleOrder(1);
        notificationService.createAndPush(order, OrderStatus.SHIPPED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("ถูกจัดส่งแล้ว");
    }

    @Test
    void createAndPush_withDeliveredStatus_shouldUseDeliveredMessage() {
        Order order = sampleOrder(1);
        notificationService.createAndPush(order, OrderStatus.DELIVERED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("ส่งถึงมือคุณแล้ว");
    }

    @Test
    void createAndPush_withCancelledStatus_shouldUseCancelledMessage() {
        Order order = sampleOrder(1);
        notificationService.createAndPush(order, OrderStatus.CANCELLED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("ถูกยกเลิก");
    }

    @Test
    void createAndPush_withRefundedStatus_shouldUseRefundedMessage() {
        Order order = sampleOrder(1);
        notificationService.createAndPush(order, OrderStatus.REFUNDED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("ได้รับการคืนเงินแล้ว");
    }
}
