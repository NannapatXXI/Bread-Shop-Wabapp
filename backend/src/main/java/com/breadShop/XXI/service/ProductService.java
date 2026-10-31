package com.breadShop.XXI.service;

import java.io.IOException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.product.ProductRequest;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;

/** สินค้า + รูปสินค้า (เก็บไฟล์ในโฟลเดอร์ uploads, DB เก็บแค่ path) */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ImageStorage      imageStorage;

    public ProductService(ProductRepository productRepository, ImageStorage imageStorage) {
        this.productRepository = productRepository;
        this.imageStorage      = imageStorage;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /** สินค้าที่ลูกค้าเห็น — ไม่รวมที่ปิดการขาย (admin ใช้ getAllProducts) */
    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    /** เปิด/ปิดการขาย — ปิดแทนการลบ เมนูที่กลับมาขายซ้ำเปิดกลับได้ */
    public Product setActive(Long id, boolean active) {
        Product product = findProduct(id);
        product.setActive(active);
        return productRepository.save(product);
    }

    public Product getProductById(Long id) {
        return findProduct(id);
    }

    /**
     * สร้างสินค้าใหม่ — ถ้ามีรูป จะบันทึกไฟล์ลงโฟลเดอร์ uploads และเก็บ path ใน DB
     * @param request ข้อมูลสินค้า + รูป (MultipartFile) จาก client
     * @return Product ที่สร้างแล้ว 
     * @throws IOException ถ้า save รูปไม่สำเร็จ
     */
    public Product createProduct(ProductRequest request) throws IOException {
        Product product = new Product();
        copyFields(request, product);
        product.setImageUrl(ImageStorage.hasFile(request.getImage()) ? imageStorage.save(request.getImage()) : null);
        return productRepository.save(product);
    }

    /**
     * แก้ไขสินค้า — ถ้ามีรูปใหม่ จะบันทึกไฟล์ใหม่และลบไฟล์เก่า 
     * @param id รหัสสินค้า
     * @param request ข้อมูลสินค้า + รูป (MultipartFile) จาก client
     * @return Product ที่แก้ไขแล้ว
     * @throws IOException ถ้า save รูปไม่สำเร็จ
     */
    public Product updateProduct(Long id, ProductRequest request) throws IOException {
        Product product = findProduct(id);
        copyFields(request, product);

        if (ImageStorage.hasFile(request.getImage())) {
            String newImageUrl = imageStorage.save(request.getImage());   // เช็คชนิดไฟล์ผ่านก่อน ค่อยลบรูปเดิม
            imageStorage.delete(product.getImageUrl());
            product.setImageUrl(newImageUrl);
        }
        return productRepository.save(product);
    }

    /**
     * ลบถาวร — ได้เฉพาะสินค้าที่ยังไม่เคยมีใครสั่ง (order_lines อ้างถึงด้วย foreign key)
     * เคยถูกสั่งแล้ว → 409 ให้ปิดการขายแทน
     * ลบแถวใน DB ก่อน แล้วค่อยลบรูป — ถ้าลบใน DB ไม่ผ่าน รูปต้องยังอยู่
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        try {
            productRepository.delete(product);
            productRepository.flush();   // ให้ FK error เกิดตรงนี้ (ไม่ใช่ตอน commit หลังจากลบรูปไปแล้ว)
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "สินค้านี้มีในออเดอร์แล้ว ลบไม่ได้ — ใช้ \"ปิดการขาย\" แทน", e);
        }
        imageStorage.delete(product.getImageUrl());
    }

    public Long countProductCategory(ProductCategory category) {
        return productRepository.countByCategory(category);
    }

    /** จำนวนสินค้าที่สต็อกมากกว่า stock */
    public Long countProductsByStock(int stock) {
        return productRepository.countByStockGreaterThan(stock);
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private static void copyFields(ProductRequest from, Product to) {
        to.setName(from.getName());
        to.setPrice(from.getPrice());
        to.setStock(from.getStock());
        to.setDescription(from.getDescription());
        to.setCategory(from.getCategory());
        to.setExpiryDate(from.getExpiryDate());
    }
}
