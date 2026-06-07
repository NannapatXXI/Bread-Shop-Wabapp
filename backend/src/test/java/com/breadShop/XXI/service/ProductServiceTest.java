package com.breadShop.XXI.service;

import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductService
 * ใช้ Mockito mock ProductRepository — ไม่แตะ DB จริง
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product(
                "Croissant",
                new BigDecimal("45.00"),
                10,
                "อร่อยมาก",
                null,
                ProductCategory.BREAD,
                LocalDate.of(2026, 12, 31)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  getAllProducts
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllProducts — คืนรายการสินค้าทั้งหมด")
    void getAllProducts_shouldReturnAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

        List<Product> result = productService.getAllProducts();

        assertThat(result).hasSize(1).contains(sampleProduct);
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("getAllProducts — ถ้าไม่มีสินค้าเลย คืน list ว่าง")
    void getAllProducts_whenEmpty_shouldReturnEmptyList() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(productService.getAllProducts()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  getProductById
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProductById — พบสินค้า → คืน Product")
    void getProductById_whenFound_shouldReturnProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        Product result = productService.getProductById(1L);

        assertThat(result).isEqualTo(sampleProduct);
        assertThat(result.getName()).isEqualTo("Croissant");
    }

    @Test
    @DisplayName("getProductById — ไม่พบสินค้า → throw 404 ResponseStatusException")
    void getProductById_whenNotFound_shouldThrow404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not Found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  deleteProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteProduct — พบสินค้า (ไม่มีรูป) → ลบสำเร็จ")
    void deleteProduct_withNoImage_shouldDeleteSuccessfully() {
        sampleProduct.setImageUrl(null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        productService.deleteProduct(1L);

        verify(productRepository).delete(sampleProduct);
    }

    @Test
    @DisplayName("deleteProduct — ไม่พบสินค้า → throw 404")
    void deleteProduct_whenNotFound_shouldThrow404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(ResponseStatusException.class);

        verify(productRepository, never()).delete(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  countAllProducts
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("countAllProducts — คืนจำนวนสินค้าทั้งหมด")
    void countAllProducts_shouldReturnTotalCount() {
        when(productRepository.count()).thenReturn(8L);

        assertThat(productService.countAllProducts()).isEqualTo(8L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  countProductCategory
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("countProductCategory — คืนจำนวนสินค้าตามหมวดหมู่ที่กำหนด")
    void countProductCategory_shouldReturnCountForGivenCategory() {
        when(productRepository.countByCategory(ProductCategory.BREAD)).thenReturn(5L);
        when(productRepository.countByCategory(ProductCategory.CAKE)).thenReturn(2L);

        assertThat(productService.countProductCategory(ProductCategory.BREAD)).isEqualTo(5L);
        assertThat(productService.countProductCategory(ProductCategory.CAKE)).isEqualTo(2L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  countProductsByStock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("countProductsByStock — คืนจำนวนสินค้าที่มี stock มากกว่าค่าที่กำหนด")
    void countProductsByStock_shouldReturnCount() {
        when(productRepository.countByStockGreaterThan(0)).thenReturn(6L);

        assertThat(productService.countProductsByStock(0)).isEqualTo(6L);
    }
}
