package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.OrderLineRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLineServiceTest {

    @Mock
    private OrderLineRepository orderLineRepository;

    private OrderLineService orderLineService;

    @BeforeEach
    void setUp() {
        orderLineService = new OrderLineService(orderLineRepository);
    }

    private static final int OWNER_ID = 1;
    private static final int ORDER_ID = 3;

    /** line อยู่ใน order ORDER_ID ของ user OWNER_ID */
    private OrderLine sampleOrderLine() {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "user", owner);

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 5L);
        product.setImageUrl("http://img/bread.png");

        OrderLine line = new OrderLine(order, product, "Bread", BigDecimal.TEN, 2, BigDecimal.valueOf(20));
        line.setProductSku("SKU-1");
        line.setDiscountAmount(BigDecimal.ONE);
        return line;
    }

    @Test
    void getByOrderId_shouldReturnMappedResponses() {
        when(orderLineRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(sampleOrderLine()));

        List<OrderLineResponse> result = orderLineService.getByOrderId(ORDER_ID, OWNER_ID);

        assertThat(result).hasSize(1);
        OrderLineResponse response = result.get(0);
        assertEquals(5, response.getProductId());
        assertEquals("Bread", response.getProductName());
        assertEquals("SKU-1", response.getProductSku());
        assertEquals("http://img/bread.png", response.getProductImageUrl());
        assertEquals(BigDecimal.TEN, response.getUnitPrice());
        assertEquals(2, response.getQuantity());
        assertEquals(BigDecimal.ONE, response.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(20), response.getTotalPrice());
    }

    @Test
    void getByOrderId_whenNoLines_shouldReturnEmptyList() {
        when(orderLineRepository.findByOrderId(anyInt())).thenReturn(List.of());

        List<OrderLineResponse> result = orderLineService.getByOrderId(1, OWNER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getByOrderId_someoneElsesOrder_shouldThrowNotFound() {
        when(orderLineRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(sampleOrderLine()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderLineService.getByOrderId(ORDER_ID, 2));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getById_whenFound_shouldReturnResponse() {
        when(orderLineRepository.findById(10)).thenReturn(Optional.of(sampleOrderLine()));

        OrderLineResponse response = orderLineService.getById(ORDER_ID, 10, OWNER_ID);

        assertEquals("Bread", response.getProductName());
    }

    @Test
    void getById_lineOfSomeoneElsesOrder_shouldThrowNotFound() {
        when(orderLineRepository.findById(10)).thenReturn(Optional.of(sampleOrderLine()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderLineService.getById(ORDER_ID, 10, 2));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getById_lineRequestedThroughAnotherOrderPath_shouldThrowNotFound() {
        // เจ้าของถูก แต่ line ไม่ได้อยู่ใน order ตาม URL
        when(orderLineRepository.findById(10)).thenReturn(Optional.of(sampleOrderLine()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderLineService.getById(99, 10, OWNER_ID));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getById_whenNotFound_shouldThrowNotFound() {
        when(orderLineRepository.findById(anyInt())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderLineService.getById(ORDER_ID, 999, OWNER_ID));

        assertEquals(404, ex.getStatusCode().value());
    }
}
