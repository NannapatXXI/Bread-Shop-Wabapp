package com.breadShop.XXI.controller;

import java.util.Locale;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.mapper.ProductMapper;
import com.breadShop.XXI.service.ProductService;

/**
 * [Claude] ProductController — endpoints สาธารณะสำหรับ user ทั่วไป (login แล้ว)
 * path: /api/v1/products/**
 * security: ต้อง login แต่ไม่ต้องเป็น admin
 *
 * แตกต่างจาก AdminController:
 *   - ดูสินค้าได้ แต่ไม่สามารถเพิ่ม/แก้ไข/ลบ
 *   - ถ้าต้องการ endpoint ที่ต้อง admin ให้ไปที่ /api/v1/admin/products
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/v1/products
     * ดึงสินค้าทั้งหมด — user ที่ login แล้วเข้าได้
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> responses = productService.getAllProducts().stream()
                .map(ProductMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    /**
     * GET /api/v1/products/get-count-Category?categoryString=BREAD
     * นับสินค้าตาม category (ใช้ใน dashboard / ทดสอบ)
     */
    @GetMapping("/get-count-Category")
    public ResponseEntity<ApiResponse<Long>> countByCategory(@RequestParam String categoryString) {
        ProductCategory category;
        try {
            category = ProductCategory.valueOf(categoryString.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ไม่พบหมวดหมู่สินค้า: " + categoryString));
        }
        Long count = productService.countProductCategory(category);
        return ResponseEntity.ok(ApiResponse.ok(
                "จำนวนสินค้าประเภท " + categoryString, count));
    }

    /**
     * GET /api/v1/products/get-count-stock?stock=5
     * นับสินค้าที่มี stock มากกว่าค่าที่กำหนด
     */
    @GetMapping("/get-count-stock")
    public ResponseEntity<ApiResponse<Long>> countByStock(@RequestParam int stock) {
        Long count = productService.countProductsByStock(stock);
        return ResponseEntity.ok(ApiResponse.ok(
                "จำนวนสินค้าที่มี stock > " + stock, count));
    }
}
