package com.breadShop.XXI.service;

import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

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

    // ตัวจริง — เทสรูปสินค้าตรวจไฟล์ที่ถูกบันทึกผ่าน MultipartFile mock
    @Spy
    private ImageStorage imageStorage = new ImageStorage();

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
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
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

    @Test
    @DisplayName("deleteProduct — สินค้าเคยถูกสั่งแล้ว (FK order_lines) → 409 ให้ปิดการขายแทน และรูปต้องยังอยู่")
    void deleteProduct_whenAlreadyOrdered_returns409_andKeepsImage() {
        sampleProduct.setImageUrl("uploads/keep-me.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        doThrow(new DataIntegrityViolationException("fk_order_lines_product")).when(productRepository).delete(sampleProduct);

        assertThatThrownBy(() -> productService.deleteProduct(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("ปิดการขาย");
                });

        verify(imageStorage, never()).delete(any());
    }

    @Test
    @DisplayName("deleteProduct — ลบแถวใน DB ก่อน แล้วค่อยลบรูป")
    void deleteProduct_deletesRowBeforeImage() {
        sampleProduct.setImageUrl("uploads/old.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        doNothing().when(imageStorage).delete(any());

        productService.deleteProduct(1L);

        InOrder order = inOrder(productRepository, imageStorage);
        order.verify(productRepository).delete(sampleProduct);
        order.verify(imageStorage).delete("uploads/old.png");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  เปิด/ปิดการขาย
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveProducts — ลูกค้าเห็นแค่สินค้าที่เปิดขาย")
    void getActiveProducts_usesActiveOnlyQuery() {
        when(productRepository.findByActiveTrue()).thenReturn(List.of(sampleProduct));

        assertThat(productService.getActiveProducts()).containsExactly(sampleProduct);
        verify(productRepository, never()).findAll();
    }

    @Test
    @DisplayName("setActive — ปิดแล้วเปิดกลับได้ ข้อมูลเดิมไม่หาย")
    void setActive_closesAndReopens_keepingData() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(sampleProduct)).thenReturn(sampleProduct);

        assertThat(productService.setActive(1L, false).isActive()).isFalse();
        assertThat(productService.setActive(1L, true).isActive()).isTrue();
        assertThat(sampleProduct.getName()).isEqualTo("Croissant");
        assertThat(sampleProduct.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("setActive — ไม่พบสินค้า → 404")
    void setActive_unknownProduct_returns404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.setActive(999L, false)).isInstanceOf(ResponseStatusException.class);
        verify(productRepository, never()).save(any());
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

    // ─────────────────────────────────────────────────────────────────────────
    //  createProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createProduct — สร้างสินค้าใหม่พร้อมอัปโหลดรูปสำเร็จ")
    void createProduct_shouldSaveProductWithImage() throws Exception {
        MultipartFile image = mock(MultipartFile.class);
        when(image.getOriginalFilename()).thenReturn("bread.png");

        com.breadShop.XXI.dto.product.ProductRequest request = new com.breadShop.XXI.dto.product.ProductRequest();
        request.setName("Bagel");
        request.setPrice(new BigDecimal("30.00"));
        request.setStock(5);
        request.setDescription("อร่อย");
        request.setCategory(ProductCategory.BREAD);
        request.setExpiryDate(LocalDate.of(2026, 12, 31));
        request.setImage(image);

        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(request);

        assertThat(result.getName()).isEqualTo("Bagel");
        assertThat(result.getImageUrl()).startsWith("uploads/");
        verify(image).transferTo(any(java.io.File.class));
        verify(productRepository).save(any(Product.class));
    }

    private com.breadShop.XXI.dto.product.ProductRequest requestWithImage(MultipartFile image) {
        com.breadShop.XXI.dto.product.ProductRequest request = new com.breadShop.XXI.dto.product.ProductRequest();
        request.setName("Bagel");
        request.setPrice(new BigDecimal("30.00"));
        request.setStock(5);
        request.setCategory(ProductCategory.BREAD);
        request.setImage(image);
        return request;
    }

    @Test
    @DisplayName("createProduct — ชื่อไฟล์บน server สร้างเอง ไม่ใช้ชื่อ (และ path) ที่ client ส่งมา")
    void createProduct_storesUnderAServerGeneratedName() throws Exception {
        MultipartFile image = mock(MultipartFile.class);
        when(image.getOriginalFilename()).thenReturn("../../evil.png");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(requestWithImage(image));

        assertThat(result.getImageUrl()).matches("uploads/[0-9a-f-]{36}\\.png");
        org.mockito.ArgumentCaptor<java.io.File> dest = org.mockito.ArgumentCaptor.forClass(java.io.File.class);
        verify(image).transferTo(dest.capture());
        assertThat(dest.getValue().toPath().getParent()).isEqualTo(com.breadShop.XXI.config.UploadPaths.dir());
    }

    @Test
    @DisplayName("createProduct — ไฟล์ที่ไม่ใช่รูป (.html) ถูกปฏิเสธ ไม่บันทึกทั้งไฟล์และสินค้า")
    void createProduct_rejectsNonImageFiles() throws Exception {
        MultipartFile page = mock(MultipartFile.class);
        when(page.getOriginalFilename()).thenReturn("promo.html");

        assertThatThrownBy(() -> productService.createProduct(requestWithImage(page)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_IMAGE_TYPE");
        verify(page, never()).transferTo(any(java.io.File.class));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProduct — ไม่มีรูป → สร้างสินค้าได้ (ไม่ crash)")
    void createProduct_withoutImage_savesWithoutImageUrl() throws Exception {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(requestWithImage(null));

        assertThat(result.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("updateProduct — รูปใหม่ผิดชนิด → รูปเดิมยังอยู่ ไม่ถูกลบ")
    void updateProduct_withInvalidNewImage_keepsTheOldImage() throws Exception {
        sampleProduct.setImageUrl("uploads/keep.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        MultipartFile bad = mock(MultipartFile.class);
        when(bad.isEmpty()).thenReturn(false);
        when(bad.getOriginalFilename()).thenReturn("x.svg");

        assertThatThrownBy(() -> productService.updateProduct(1L, requestWithImage(bad)))
                .hasMessage("INVALID_IMAGE_TYPE");
        assertThat(sampleProduct.getImageUrl()).isEqualTo("uploads/keep.png");
        verify(productRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  updateProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProduct — ไม่พบสินค้า → throw 404")
    void updateProduct_whenNotFound_shouldThrow404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        com.breadShop.XXI.dto.product.ProductRequest request = new com.breadShop.XXI.dto.product.ProductRequest();

        assertThatThrownBy(() -> productService.updateProduct(999L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("updateProduct — ไม่มีรูปใหม่ → อัปเดตข้อมูลอื่นโดยไม่แตะรูป")
    void updateProduct_withoutNewImage_shouldUpdateFieldsOnly() throws Exception {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        com.breadShop.XXI.dto.product.ProductRequest request = new com.breadShop.XXI.dto.product.ProductRequest();
        request.setName("Updated Name");
        request.setPrice(new BigDecimal("99.00"));
        request.setStock(3);
        request.setDescription("desc ใหม่");
        request.setCategory(ProductCategory.CAKE);
        request.setExpiryDate(LocalDate.of(2027, 1, 1));

        Product result = productService.updateProduct(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getCategory()).isEqualTo(ProductCategory.CAKE);
    }

    @Test
    @DisplayName("updateProduct — มีรูปใหม่และของเดิมมีรูปอยู่แล้ว → ลบรูปเก่าและบันทึกรูปใหม่")
    void updateProduct_withNewImage_shouldReplaceImage() throws Exception {
        sampleProduct.setImageUrl("uploads/old.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile newImage = mock(MultipartFile.class);
        when(newImage.isEmpty()).thenReturn(false);
        when(newImage.getOriginalFilename()).thenReturn("new.png");

        com.breadShop.XXI.dto.product.ProductRequest request = new com.breadShop.XXI.dto.product.ProductRequest();
        request.setName("Croissant");
        request.setPrice(new BigDecimal("45.00"));
        request.setStock(10);
        request.setDescription("อร่อยมาก");
        request.setCategory(ProductCategory.BREAD);
        request.setExpiryDate(LocalDate.of(2026, 12, 31));
        request.setImage(newImage);

        Product result = productService.updateProduct(1L, request);

        assertThat(result.getImageUrl()).startsWith("uploads/");
        verify(newImage).transferTo(any(java.io.File.class));
    }

    @Test
    @DisplayName("deleteProduct — พบสินค้าที่มีรูปภาพ → ลบรูปภาพและลบสินค้า")
    void deleteProduct_withImage_shouldDeleteImageAndProduct() {
        sampleProduct.setImageUrl("uploads/existing.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        productService.deleteProduct(1L);

        verify(productRepository).delete(sampleProduct);
    }

    @Test
    @DisplayName("updateProduct — request มี image แต่เป็นไฟล์ว่าง → ไม่แตะรูปเดิม")
    void updateProduct_withEmptyImage_shouldNotReplaceImage() throws Exception {
        sampleProduct.setImageUrl("uploads/keep.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile emptyImage = mock(MultipartFile.class);
        when(emptyImage.isEmpty()).thenReturn(true);

        com.breadShop.XXI.dto.product.ProductRequest request = new com.breadShop.XXI.dto.product.ProductRequest();
        request.setName("Croissant");
        request.setPrice(new BigDecimal("45.00"));
        request.setStock(10);
        request.setDescription("อร่อยมาก");
        request.setCategory(ProductCategory.BREAD);
        request.setExpiryDate(LocalDate.of(2026, 12, 31));
        request.setImage(emptyImage);

        Product result = productService.updateProduct(1L, request);

        assertThat(result.getImageUrl()).isEqualTo("uploads/keep.png");
        verify(emptyImage, never()).transferTo(any(java.io.File.class));
    }
}
