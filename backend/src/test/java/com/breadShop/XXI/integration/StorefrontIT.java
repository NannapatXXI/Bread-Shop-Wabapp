package com.breadShop.XXI.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.service.Mailservice;
import com.breadShop.XXI.service.StorefrontService;

import jakarta.persistence.EntityManager;

/** สินค้าแนะนำบน MySQL จริง — ลำดับต้องถูกบันทึกลง DB จริง (อ่านใหม่หลัง clear persistence context) */
@SpringBootTest
@Transactional
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Storefront (featured products) Integration Tests")
class StorefrontIT {

    @Autowired private StorefrontService storefrontService;
    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean private Mailservice mailservice;

    private Product bread;
    private Product cake;
    private Product cookie;

    @BeforeEach
    void setUp() {
        bread  = save("SF Bread", 10);
        cake   = save("SF Cake", 0);     // หมด
        cookie = save("SF Cookie", 3);
    }

    private Product save(String name, int stock) {
        return productRepository.save(new Product(name, new BigDecimal("20.00"), stock, "", null,
                ProductCategory.BREAD, LocalDate.of(2027, 1, 1)));
    }

    /** ส่ง SQL ที่ค้างไป DB แล้วล้าง cache ของ Hibernate — ค่าที่อ่านต่อจากนี้มาจาก DB จริง */
    private void reloadFromDb() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<Long> picksInDb() {
        return productRepository.findByFeaturedRankIsNotNullOrderByFeaturedRankAsc().stream().map(Product::getId).toList();
    }

    @Test
    @DisplayName("ตั้งสินค้าแนะนำ → ลำดับถูกบันทึกลง DB")
    void setFeatured_persistsTheOrder() {
        storefrontService.setFeatured(List.of(cookie.getId(), bread.getId()));
        reloadFromDb();

        assertThat(picksInDb()).containsExactly(cookie.getId(), bread.getId());
    }

    @Test
    @DisplayName("ตั้งใหม่โดยตัวแรกได้ลำดับเดิม → ยังเป็นสินค้าแนะนำ (ไม่หลุดเป็น NULL)")
    void setFeaturedAgain_keepsItemsWhoseRankDidNotChange() {
        storefrontService.setFeatured(List.of(bread.getId(), cake.getId()));
        reloadFromDb();

        storefrontService.setFeatured(List.of(bread.getId(), cookie.getId()));   // bread ยังลำดับ 0 เหมือนเดิม
        reloadFromDb();

        assertThat(picksInDb()).containsExactly(bread.getId(), cookie.getId());
    }

    @Test
    @DisplayName("ลูกค้าไม่เห็นสินค้าแนะนำที่หมด")
    void storefront_hidesSoldOutPicks() {
        storefrontService.setFeatured(List.of(cake.getId(), cookie.getId(), bread.getId()));
        reloadFromDb();

        List<Long> shown = storefrontService.getStorefront().featured().stream().map(ProductResponse::getId).toList();

        assertThat(shown).startsWith(cookie.getId(), bread.getId()).doesNotContain(cake.getId());
    }
}
