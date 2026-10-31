package com.breadShop.XXI.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.dto.customer.UserOrderCount;
import com.breadShop.XXI.dto.dashboard.CategoryRevenue;
import com.breadShop.XXI.dto.dashboard.TopProductStat;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.OrderLineRepository;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.Mailservice;

/**
 * query ที่คืน record ด้วย SELECT new — ต้องรันบน MySQL จริงถึงจะรู้ว่าชนิดของ SUM/COUNT ตรงกับ constructor
 * (ชนิดไม่ตรง Hibernate ไม่ยอม start แอปเลย ส่วนค่าผิดจับได้จากเทสนี้)
 */
@SpringBootTest
@Transactional
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Aggregate queries (SELECT new record)")
class AggregateQueriesIT {

    private static final List<OrderStatus> EXCLUDED = List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    @Autowired private UserRepository        userRepository;
    @Autowired private UserAddressRepository addressRepository;
    @Autowired private ProductRepository     productRepository;
    @Autowired private OrderRepository       orderRepository;
    @Autowired private OrderLineRepository   orderLineRepository;

    @MockitoBean private Mailservice mailservice;

    private User alice;
    private User bob;
    private UserAddress aliceAddress;
    private UserAddress bobAddress;
    private Product bread;
    private Product cake;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("agg_alice", "agg_alice@breadshop.test", "x"));
        bob   = userRepository.save(new User("agg_bob", "agg_bob@breadshop.test", "x"));
        aliceAddress = addressRepository.save(new UserAddress(alice, "บ้าน", "Alice", "0800000000",
                "1 Road", "กรุงเทพมหานคร", "เขต", "แขวง", "10110", true));
        bobAddress = addressRepository.save(new UserAddress(bob, "บ้าน", "Bob", "0800000001",
                "2 Road", "กรุงเทพมหานคร", "เขต", "แขวง", "10110", true));
        bread = productRepository.save(new Product("Agg Bread", new BigDecimal("20.00"), 50, "", "uploads/bread.jpg",
                ProductCategory.BREAD, LocalDate.of(2027, 1, 1)));
        cake  = productRepository.save(new Product("Agg Cake", new BigDecimal("80.00"), 50, "", null,
                ProductCategory.CAKE, LocalDate.of(2027, 1, 1)));

        // Alice: 2 order (ขนมปัง 3 + เค้ก 1, ขนมปัง 2) / Bob: 1 order ที่ถูกยกเลิก (ขนมปัง 10 — ต้องไม่นับเป็นยอดขาย)
        order(alice, aliceAddress, OrderStatus.DELIVERED, line(bread, 3), line(cake, 1));
        order(alice, aliceAddress, OrderStatus.PENDING, line(bread, 2));
        order(bob, bobAddress, OrderStatus.CANCELLED, line(bread, 10));
    }

    private record Item(Product product, int qty) {}

    private static Item line(Product product, int qty) {
        return new Item(product, qty);
    }

    private void order(User user, UserAddress address, OrderStatus status, Item... items) {
        Order order = new Order(user, "ORD-AGG-" + System.nanoTime(), address);
        order.setStatus(status);
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : items) {
            BigDecimal lineTotal = item.product().getPrice().multiply(BigDecimal.valueOf(item.qty()));
            OrderLine line = new OrderLine(order, item.product(), item.product().getName(),
                    item.product().getPrice(), item.qty(), lineTotal);
            line.setDiscountAmount(BigDecimal.ZERO);
            order.getOrderLines().add(line);
            total = total.add(lineTotal);
        }
        order.setSubtotal(total);
        order.setTotalAmount(total);
        orderRepository.saveAndFlush(order);
    }

    private static LocalDateTime from() { return LocalDateTime.now().minusDays(1); }
    private static LocalDateTime to()   { return LocalDateTime.now().plusDays(1); }

    @Test
    @DisplayName("countOrdersGroupByUser → จำนวน order ต่อ user (นับทุกสถานะ)")
    void countOrdersGroupByUser() {
        List<UserOrderCount> rows = orderRepository.countOrdersGroupByUser();

        assertThat(rows).contains(new UserOrderCount(alice.getId(), 2L), new UserOrderCount(bob.getId(), 1L));
    }

    @Test
    @DisplayName("sumRevenueByCategory → ยอดขายต่อหมวด ไม่นับ order ที่ยกเลิก")
    void sumRevenueByCategory() {
        List<CategoryRevenue> rows = orderLineRepository.sumRevenueByCategory(from(), to(), EXCLUDED);

        // ขนมปัง (3 + 2) × 20 = 100 (ของ Bob 10 ชิ้นถูกยกเลิก) / เค้ก 1 × 80 = 80
        assertThat(rows).hasSize(2);
        assertThat(revenueOf(rows, ProductCategory.BREAD)).isEqualByComparingTo("100.00");
        assertThat(revenueOf(rows, ProductCategory.CAKE)).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("getTopProductStats → เรียงจากจำนวนชิ้นมากไปน้อย พร้อมยอดเงินและรูป")
    void getTopProductStats() {
        List<TopProductStat> rows = orderLineRepository.getTopProductStats(from(), to(), EXCLUDED);

        assertThat(rows).hasSize(2);
        TopProductStat top = rows.get(0);
        assertThat(top.productId()).isEqualTo(bread.getId());
        assertThat(top.productName()).isEqualTo("Agg Bread");
        assertThat(top.totalQty()).isEqualTo(5L);
        assertThat(top.totalRevenue()).isEqualByComparingTo("100.00");
        assertThat(top.imageUrl()).isEqualTo("uploads/bread.jpg");
        assertThat(rows.get(1).productName()).isEqualTo("Agg Cake");
        assertThat(rows.get(1).imageUrl()).isNull();
    }

    private static BigDecimal revenueOf(List<CategoryRevenue> rows, ProductCategory category) {
        return rows.stream().filter(r -> r.category() == category).findFirst().orElseThrow().revenue();
    }
}
