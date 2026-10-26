package com.breadShop.XXI.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.config.UploadPaths;
import com.breadShop.XXI.dto.product.ProductRequest;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;

/** สินค้า + รูปสินค้า (เก็บไฟล์ในโฟลเดอร์ uploads, DB เก็บแค่ path) */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Path UPLOAD_DIR = UploadPaths.dir();

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return findProduct(id);
    }

    public Product createProduct(ProductRequest request) throws IOException {
        Product product = new Product();
        copyFields(request, product);
        product.setImageUrl(hasImage(request) ? saveImage(request.getImage()) : null);
        return productRepository.save(product);
    }

    /** แก้ทุกช่อง — ถ้าส่งรูปใหม่มา จะลบรูปเก่าทิ้ง */
    public Product updateProduct(Long id, ProductRequest request) throws IOException {
        Product product = findProduct(id);
        copyFields(request, product);

        if (hasImage(request)) {
            String newImageUrl = saveImage(request.getImage());   // เช็คชนิดไฟล์ผ่านก่อน ค่อยลบรูปเดิม
            deleteImage(product.getImageUrl());
            product.setImageUrl(newImageUrl);
        }
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        deleteImage(product.getImageUrl());
        productRepository.delete(product);
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

    private static boolean hasImage(ProductRequest request) {
        return request.getImage() != null && !request.getImage().isEmpty();
    }

    /** บันทึกรูปด้วยชื่อที่ server สร้างเอง (ไม่ใช้ชื่อไฟล์จาก client) @return path ที่เก็บใน DB */
    private String saveImage(MultipartFile image) throws IOException {
        String fileName = UploadPaths.newFileName(image.getOriginalFilename());
        image.transferTo(UploadPaths.resolveInside(UPLOAD_DIR, fileName).toFile());
        return "uploads/" + fileName;
    }

    /** ลบไม่สำเร็จแค่ log ไว้ — ไม่ควรทำให้การลบ/แก้สินค้าล้ม */
    private void deleteImage(String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        try {
            Files.deleteIfExists(UploadPaths.resolveInside(UPLOAD_DIR, imageUrl));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Delete image failed: {}", e.getMessage());
        }
    }
}
