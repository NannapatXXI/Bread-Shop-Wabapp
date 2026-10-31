package com.breadShop.XXI.controller;

import java.util.List;
import java.util.Locale;

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

/** /api/v1/products — ดูสินค้า (สาธารณะ ไม่ต้อง login) ส่วนเพิ่ม/แก้/ลบอยู่ที่ AdminController */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getActiveProducts().stream().map(ProductMapper::toResponse).toList()));
    }

   /**
    * เอาไว้นับจำนวนสินค้าตาม category หรือ stockAbove (stock > x) อย่างใดอย่างหนึ่ง
    * @param category หมวดหมู่สินค้า (เช่น BREAD, PASTRY, CAKE) หรือ null
    * @param stockAbove จำนวน stock ที่มากกว่า (เช่น 10) หรือ null
    * @return ResponseEntity<ApiResponse<Long>> จำนวนสินค้าที่ตรงตามเงื่อนไข
    */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> count( @RequestParam(required = false) String category, @RequestParam(required = false) Integer stockAbove) {
        if ((category == null) == (stockAbove == null)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ระบุ category หรือ stockAbove อย่างใดอย่างหนึ่ง"));
        }
        if (stockAbove != null) {
            return ResponseEntity.ok(ApiResponse.ok("จำนวนสินค้าที่มี stock > " + stockAbove,
                    productService.countProductsByStock(stockAbove)));
        }
        try {
            ProductCategory value = ProductCategory.valueOf(category.toUpperCase(Locale.ROOT));
            return ResponseEntity.ok(ApiResponse.ok("จำนวนสินค้าประเภท " + category,
                    productService.countProductCategory(value)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ไม่พบหมวดหมู่สินค้า: " + category));
        }
    }
}
