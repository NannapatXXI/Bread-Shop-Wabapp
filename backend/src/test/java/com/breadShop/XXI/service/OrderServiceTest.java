package com.breadShop.XXI.service;

import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.dto.orderline.OrderLineRequest;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.PromotionRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock private OrderRepository         orderRepository;
    @Mock private UserRepository          userRepository;
    @Mock private UserAddressRepository   userAddressRepository;
    @Mock private ProductRepository       productRepository;
    @Mock private PromotionRepository     promotionRepository;
    @Mock private PromotionService        promotionService;
    @Mock private NotificationService     notificationService;
    @Mock private OrderLogService         orderLogService;
    @Mock private UserActivityLogService  activityLogService;

    @InjectMocks
    private OrderService orderService;

    private User sampleUser;
    private UserAddress sampleAddress;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleUser = new User("nannapat", "nannapat@breadshop.com", "hashed_pw");
        ReflectionTestUtils.setField(sampleUser, "id", 1);

        sampleAddress = new UserAddress();
        sampleAddress.setUser(sampleUser);   // ownership check ใน createOrder ต้องการ user
        sampleAddress.setRecipientName("นันทพัทธ์");
        sampleAddress.setPhone("0812345678");
        sampleAddress.setAddress("123 ถ.สุขุมวิท");
        sampleAddress.setProvince("กรุงเทพ");
        sampleAddress.setDistrict("คลองเตย");
        sampleAddress.setSubdistrict("คลองเตย");
        sampleAddress.setPostcode("10110");

        sampleProduct = new Product(
                "Croissant", new BigDecimal("45.00"), 20,
                "อร่อย", null, ProductCategory.BREAD,
                LocalDate.of(2026, 12, 31)
        );
        ReflectionTestUtils.setField(sampleProduct, "id", 10L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  getByUserId
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByUserId — คืน list ของ OrderResponse")
    void getByUserId_shouldReturnOrderResponses() {
        Order mockOrder = buildMockOrder();
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(1)).thenReturn(List.of(mockOrder));

        List<OrderResponse> result = orderService.getByUserId(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(sampleUser.getId());
    }

    @Test
    @DisplayName("getByUserId — ไม่มี order เลย → คืน list ว่าง")
    void getByUserId_withNoOrders_shouldReturnEmptyList() {
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(99)).thenReturn(List.of());

        assertThat(orderService.getByUserId(99)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  createOrder — error cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder — ไม่พบ user → throw 404")
    void createOrder_whenUserNotFound_shouldThrow404() {
        OrderRequest request = buildOrderRequest(1, 1);
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("createOrder — ไม่พบที่อยู่ → throw 404")
    void createOrder_whenAddressNotFound_shouldThrow404() {
        OrderRequest request = buildOrderRequest(1, 999);
        when(userRepository.findById(1)).thenReturn(Optional.of(sampleUser));
        when(userAddressRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Address not found");
    }

    @Test
    @DisplayName("createOrder — stock ไม่พอ → throw 400")
    void createOrder_whenStockInsufficient_shouldThrow400() {
        sampleProduct.setStock(1);

        OrderLineRequest item = mock(OrderLineRequest.class);
        when(item.getProductId()).thenReturn(10);
        when(item.getQuantity()).thenReturn(5);

        OrderRequest request = mock(OrderRequest.class);
        when(request.getUserId()).thenReturn(1);
        when(request.getAddressId()).thenReturn(1);
        when(request.getShippingFee()).thenReturn(BigDecimal.ZERO);
        when(request.getNote()).thenReturn(null);
        when(request.getPromotionCode()).thenReturn(null);
        when(request.getItems()).thenReturn(List.of(item));

        when(userRepository.findById(1)).thenReturn(Optional.of(sampleUser));
        when(userAddressRepository.findById(1)).thenReturn(Optional.of(sampleAddress));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("createOrder — ไม่พบสินค้าใน item → throw 404")
    void createOrder_whenProductNotFound_shouldThrow404() {
        OrderLineRequest item = mock(OrderLineRequest.class);
        when(item.getProductId()).thenReturn(999);

        OrderRequest request = mock(OrderRequest.class);
        when(request.getUserId()).thenReturn(1);
        when(request.getAddressId()).thenReturn(1);
        when(request.getShippingFee()).thenReturn(BigDecimal.ZERO);
        when(request.getNote()).thenReturn(null);
        when(request.getPromotionCode()).thenReturn(null);
        when(request.getItems()).thenReturn(List.of(item));

        when(userRepository.findById(1)).thenReturn(Optional.of(sampleUser));
        when(userAddressRepository.findById(1)).thenReturn(Optional.of(sampleAddress));
        when(productRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product not found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  updateStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateStatus — ไม่พบ order → throw 404")
    void updateStatus_whenOrderNotFound_shouldThrow404() {
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.updateStatus(999, Order.OrderStatus.SHIPPED, "TH123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    @DisplayName("updateStatus — อัปเดตสถานะและ trackingNo สำเร็จ")
    void updateStatus_shouldUpdateStatusAndTrackingNo() {
        Order mockOrder = buildMockOrder();
        when(orderRepository.findById(1)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any())).thenReturn(mockOrder);

        orderService.updateStatus(1, Order.OrderStatus.SHIPPED, "TH-001");

        assertThat(mockOrder.getStatus()).isEqualTo(Order.OrderStatus.SHIPPED);
        assertThat(mockOrder.getTrackingNo()).isEqualTo("TH-001");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  cancelOrder
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder — PENDING + user เป็นเจ้าของ → เปลี่ยนเป็น CANCELLED และคืน stock")
    void cancelOrder_whenPendingAndOwner_shouldCancelAndRestoreStock() {
        Order order = buildMockOrder();
        OrderLine line = mock(OrderLine.class);
        when(line.getProduct()).thenReturn(sampleProduct);
        when(line.getQuantity()).thenReturn(2);
        order.getOrderLines().add(line);

        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        int stockBefore = sampleProduct.getStock();
        orderService.cancelOrder(1, sampleUser.getId());

        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        assertThat(sampleProduct.getStock()).isEqualTo(stockBefore + 2);
    }

    @Test
    @DisplayName("cancelOrder — user ไม่ใช่เจ้าของ order → throw 403")
    void cancelOrder_whenNotOwner_shouldThrow403() {
        Order order = buildMockOrder();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1, 999))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("cancelOrder — status ไม่ใช่ PENDING → throw 400 พร้อมบอกสถานะ")
    void cancelOrder_whenNotPending_shouldThrow400() {
        Order order = buildMockOrder();
        order.setStatus(Order.OrderStatus.PROCESSING);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1, sampleUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("cancelOrder — ไม่พบ order → throw 404")
    void cancelOrder_whenOrderNotFound_shouldThrow404() {
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(999, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  helper
    // ─────────────────────────────────────────────────────────────────────────

    private Order buildMockOrder() {
        Order order = new Order(sampleUser, "ORD-20260101000000", sampleAddress);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setStatus(Order.OrderStatus.PENDING);
        return order;
    }

    private OrderRequest buildOrderRequest(int userId, int addressId) {
        OrderRequest request = mock(OrderRequest.class);
        when(request.getUserId()).thenReturn(userId);
        when(request.getAddressId()).thenReturn(addressId);
        return request;
    }
}
