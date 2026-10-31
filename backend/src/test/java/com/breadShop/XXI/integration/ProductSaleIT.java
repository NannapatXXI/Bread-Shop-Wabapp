package com.breadShop.XXI.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.orderline.OrderLineRequest;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.Mailservice;
import com.breadShop.XXI.service.OrderService;
import com.breadShop.XXI.service.ProductService;
import com.breadShop.XXI.service.StorefrontService;

/**
 * ปิดการขาย vs ลบสินค้า บน MySQL จริง
 * ไม่ใช้ @Transactional — FK ของ order_lines ทำงานตอนเขียนลง DB จริงเท่านั้น จึง commit จริงแล้วลบทิ้งใน @AfterEach
 */
@SpringBootTest
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Close sale vs delete product (real foreign keys)")
class ProductSaleIT {

    @Autowired private ProductService productService;
    @Autowired private OrderService orderService;
    @Autowired private StorefrontService storefrontService;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserAddressRepository addressRepository;

    @MockitoBean private Mailservice mailservice;

    private User user;
    private UserAddress address;
    private Product ordered;      // เคยถูกสั่งแล้ว — มีแถวใน order_lines
    private Product neverOrdered;
    private Order order;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("sale_it_user", "sale_it@breadshop.test", "x"));
        address = addressRepository.save(new UserAddress(user, "บ้าน", "Sale IT", "0800000000",
                "1 Road", "กรุงเทพมหานคร", "เขต", "แขวง", "10110", true));
        ordered = productRepository.save(product("Sale IT Bread", 10));
        neverOrdered = productRepository.save(product("Sale IT Cake", 10));

        order = new Order(user, "ORD-SALE-IT-" + System.nanoTime(), address);
        OrderLine line = new OrderLine(order, ordered, ordered.getName(), ordered.getPrice(), 1, ordered.getPrice());
        line.setDiscountAmount(BigDecimal.ZERO);
        order.getOrderLines().add(line);
        order.setSubtotal(ordered.getPrice());
        order.setTotalAmount(ordered.getPrice());
        order = orderRepository.save(order);
    }

    @AfterEach
    void cleanUp() {
        orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).forEach(orderRepository::delete);   // order_lines ลบตาม (CASCADE)
        productRepository.findAllById(List.of(ordered.getId(), neverOrdered.getId())).forEach(productRepository::delete);
        addressRepository.deleteById(address.getId());
        userRepository.deleteById(user.getId());
    }

    private static Product product(String name, int stock) {
        return new Product(name, new BigDecimal("20.00"), stock, "", null, ProductCategory.BREAD, LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("ลบสินค้าที่เคยถูกสั่ง → 409 (ไม่ใช่ 500) และสินค้ายังอยู่")
    void deletingAnOrderedProduct_returns409_andKeepsIt() {
        assertThatThrownBy(() -> productService.deleteProduct(ordered.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(productRepository.existsById(ordered.getId())).isTrue();
    }

    @Test
    @DisplayName("ลบสินค้าที่ยังไม่เคยมีใครสั่ง → ลบได้")
    void deletingANeverOrderedProduct_works() {
        productService.deleteProduct(neverOrdered.getId());

        assertThat(productRepository.existsById(neverOrdered.getId())).isFalse();
    }

    @Test
    @DisplayName("ปิดการขาย → ลูกค้าไม่เห็นทั้งรายการสินค้าและสินค้าแนะนำ / เปิดกลับ → เห็นอีก")
    void closingSale_hidesFromCustomers_andReopeningShowsAgain() {
        productService.setActive(ordered.getId(), false);

        assertThat(productService.getActiveProducts()).extracting(Product::getId).doesNotContain(ordered.getId());
        storefrontService.setFeatured(List.of(ordered.getId()));
        assertThat(storefrontService.getStorefront().featured()).noneMatch(p -> p.getId().equals(ordered.getId()));

        productService.setActive(ordered.getId(), true);

        assertThat(productService.getActiveProducts()).extracting(Product::getId).contains(ordered.getId());
        storefrontService.setFeatured(List.of());
    }

    @Test
    @DisplayName("สั่งสินค้าที่ปิดการขาย (เช่นค้างในตะกร้า) → 400 และสต็อกไม่ถูกตัด")
    void orderingAClosedProduct_isRejected_andStockUntouched() {
        productService.setActive(neverOrdered.getId(), false);

        OrderLineRequest item = new OrderLineRequest();
        item.setProductId(neverOrdered.getId().intValue());
        item.setQuantity(2);
        OrderRequest request = new OrderRequest();
        request.setUserId(user.getId());
        request.setAddressId(address.getId());
        request.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(productRepository.findById(neverOrdered.getId()).orElseThrow().getStock()).isEqualTo(10);
    }
}
