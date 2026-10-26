package com.breadShop.XXI.controller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ActiveRequest;
import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.customer.CustomerResponse;
import com.breadShop.XXI.dto.customer.RoleRequest;
import com.breadShop.XXI.dto.customer.UsernameRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.dto.order.OrderStatusRequest;
import com.breadShop.XXI.dto.product.ProductRequest;
import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.mapper.ProductMapper;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.OrderService;
import com.breadShop.XXI.service.ProductService;

import jakarta.validation.Valid;

/**
 * Controller สำหรับ admin (ผู้ดูแลระบบ) — ต้อง login ด้วย role ADMIN 
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final ProductService  productService;
    private final OrderService    orderService;
    private final CustomerService customerService;
    private final AuditLogService auditLogService;
    private final CurrentUser     currentUser;

    public AdminController(ProductService productService, OrderService orderService, CustomerService customerService,
                           AuditLogService auditLogService, CurrentUser currentUser) {
        this.productService  = productService;
        this.orderService    = orderService;
        this.customerService = customerService;
        this.auditLogService = auditLogService;
        this.currentUser     = currentUser;
    }

    // ─── สินค้า ────────────────────────────────────────────────────────

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getAllProducts().stream().map(ProductMapper::toResponse).toList()));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ProductMapper.toResponse(productService.getProductById(id))));
    }

    @PostMapping(value = "/products", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @ModelAttribute ProductRequest request) throws IOException {
        Product product = productService.createProduct(request);
        audit("CREATE", "Product", product.getId(), null, product.getName(), "Add product: " + product.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("เพิ่มสินค้าสำเร็จ", ProductMapper.toResponse(product)));
    }

    @PutMapping(value = "/products/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable Long id, @Valid @ModelAttribute ProductRequest request) throws IOException {
        Product product = productService.updateProduct(id, request);
        audit("UPDATE", "Product", id, null, product.getName(), "Update product: " + product.getName());
        return ResponseEntity.ok(ApiResponse.ok("แก้ไขสินค้าสำเร็จ", ProductMapper.toResponse(product)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        audit("DELETE", "Product", id, null, null, "Delete product id: " + id);
        return ResponseEntity.ok(ApiResponse.ok("ลบสินค้าสำเร็จ"));
    }

    // ─── Order ─────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAll()));
    }

    /** เปลี่ยนสถานะ (+ เลข tracking ตอนส่งของ) — ลูกค้าได้แจ้งเตือนทันที */
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@PathVariable Integer id, @Valid @RequestBody OrderStatusRequest request) {
        OrderStatus status = request.status();
        OrderResponse order = orderService.updateStatus(id, status, request.trackingNo());
        audit("CHANGE_ORDER_STATUS", "Order", id, null, status.name(),
                "Order #" + order.getOrderNo() + " > " + status.name());
        return ResponseEntity.ok(ApiResponse.ok("อัปเดตสถานะสำเร็จ", order));
    }

    // ─── ลูกค้า ─────────────────────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAllCustomers() {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getAll()));
    }

    @PatchMapping("/customers/{id}/role")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(@PathVariable Integer id, @Valid @RequestBody RoleRequest request) {
        String oldRole = customerService.updateRole(id, request.role());
        audit("CHANGE_ROLE", "User", id, oldRole, request.role().toUpperCase(Locale.ROOT), "Change role for user id: " + id);
        return ResponseEntity.ok(ApiResponse.ok("อัปเดต role สำเร็จ"));
    }

    /** { "active": false } = แบน, true = ปลดแบน */
    @PatchMapping("/customers/{id}/active")
    public ResponseEntity<ApiResponse<Void>> setActive(@PathVariable Integer id, @Valid @RequestBody ActiveRequest request) {
        boolean active = request.active();
        boolean wasActive = customerService.setActive(id, active);
        String message = active ? "ปลดระงับบัญชีสำเร็จ" : "ระงับบัญชีสำเร็จ";
        audit("BAN_USER", "User", id, String.valueOf(wasActive), String.valueOf(active), message + " user id: " + id);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    @PatchMapping("/customers/{id}/username")
    public ResponseEntity<ApiResponse<Void>> updateUsername(@PathVariable Integer id,
                                                           @Valid @RequestBody UsernameRequest request) {
        customerService.updateUsername(id, request.username());
        return ResponseEntity.ok(ApiResponse.ok("อัปเดต username สำเร็จ"));
    }

    /** บันทึก audit log ในนามของ admin ที่ login อยู่ */
    private void audit(String action, String entityType, Object entityId, String oldValue, String newValue, String details) {
        User admin = currentUser.get(SecurityContextHolder.getContext().getAuthentication().getName());
        auditLogService.log(admin, action, entityType, String.valueOf(entityId), oldValue, newValue, details);
    }
}
