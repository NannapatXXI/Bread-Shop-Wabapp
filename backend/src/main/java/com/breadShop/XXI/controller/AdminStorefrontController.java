package com.breadShop.XXI.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.storefront.AdminStorefrontResponse;
import com.breadShop.XXI.dto.storefront.FeaturedRequest;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.StorefrontService;

import jakarta.validation.Valid;

/** /api/v1/admin/storefront — admin ตั้งรูปปกและสินค้าแนะนำ (ROLE_ADMIN — SecurityConfig) ทุกการแก้ลง audit log */
@RestController
@RequestMapping("/api/v1/admin/storefront")
public class AdminStorefrontController {

    private final StorefrontService storefrontService;
    private final AuditLogService   auditLogService;
    private final CustomerService   customerService;

    public AdminStorefrontController(StorefrontService storefrontService, AuditLogService auditLogService,
                                     CustomerService customerService) {
        this.storefrontService = storefrontService;
        this.auditLogService   = auditLogService;
        this.customerService   = customerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminStorefrontResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(storefrontService.getForAdmin()));
    }

    /** body { "productIds": [3, 1, 7] } — ลำดับใน list = ลำดับที่แสดง */
    @PutMapping("/featured")
    public ResponseEntity<ApiResponse<Void>> setFeatured(@AuthenticationPrincipal String email,
                                                         @Valid @RequestBody FeaturedRequest request) {
        storefrontService.setFeatured(request.productIds());
        audit(email, "UPDATE_FEATURED", request.productIds().toString(), "Featured products: " + request.productIds());
        return ResponseEntity.ok(ApiResponse.ok("บันทึกสินค้าแนะนำสำเร็จ"));
    }

    @PutMapping(value = "/hero-image", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> setHeroImage(@AuthenticationPrincipal String email,
                                                                         @RequestParam("image") MultipartFile image)
            throws IOException {
        String url = storefrontService.setHeroImage(image);
        audit(email, "UPDATE_HERO_IMAGE", url, "Change home cover image");
        return ResponseEntity.ok(ApiResponse.ok("เปลี่ยนรูปปกสำเร็จ", Map.of("heroImageUrl", url)));
    }

    @DeleteMapping("/hero-image")
    public ResponseEntity<ApiResponse<Void>> removeHeroImage(@AuthenticationPrincipal String email) {
        storefrontService.removeHeroImage();
        audit(email, "REMOVE_HERO_IMAGE", null, "Use default home cover image");
        return ResponseEntity.ok(ApiResponse.ok("กลับไปใช้รูปปกตั้งต้นแล้ว"));
    }

    private void audit(String email, String action, String newValue, String details) {
        auditLogService.log(customerService.getByEmail(email), action, "Storefront", "home", null, newValue, details);
    }
}
