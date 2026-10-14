package com.breadShop.XXI.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.breadShop.XXI.dto.log.OrderLogResponse;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLog;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.OrderLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLogServiceTest {

    @Mock
    private OrderLogRepository repo;

    private OrderLogService orderLogService;

    @BeforeEach
    void setUp() {
        orderLogService = new OrderLogService(repo);
    }

    private Order sampleOrder() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 1);
        ReflectionTestUtils.setField(order, "orderNo", "ORD-1");
        return order;
    }

    @Test
    void log_withChangedByUser_shouldSaveWithUsername() {
        Order order = sampleOrder();
        User changedBy = new User();
        changedBy.setUsername("admin1");

        orderLogService.log(order, "PENDING", "SHIPPED", changedBy, "TRACK-1", "note");

        ArgumentCaptor<OrderLog> captor = ArgumentCaptor.forClass(OrderLog.class);
        verify(repo).save(captor.capture());
        OrderLog saved = captor.getValue();
        assertEquals("admin1", saved.getChangedByName());
        assertEquals("SHIPPED", saved.getNewStatus());
        assertEquals("ORD-1", saved.getOrderNo());
    }

    @Test
    void log_withNullChangedBy_shouldSaveWithSystemDefault() {
        Order order = sampleOrder();

        orderLogService.log(order, "PENDING", "CANCELLED", null, null, null);

        ArgumentCaptor<OrderLog> captor = ArgumentCaptor.forClass(OrderLog.class);
        verify(repo).save(captor.capture());
        assertEquals("system", captor.getValue().getChangedByName());
    }

    @Test
    void log_whenRepoThrows_shouldNotPropagateException() {
        Order order = sampleOrder();
        doThrow(new RuntimeException("db down")).when(repo).save(any());

        assertDoesNotThrow(() -> orderLogService.log(order, "PENDING", "SHIPPED", null, null, null));
    }

    @Test
    void search_shouldReturnMappedPage() {
        OrderLog entity = new OrderLog(sampleOrder(), "ORD-1", null, "PENDING", "SHIPPED", null, "system", null, null);
        when(repo.search(isNull(), eq("ORD-1"), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<OrderLogResponse> result = orderLogService.search(null, "ORD-1", null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertEquals("SHIPPED", result.getContent().get(0).newStatus());
    }
}
