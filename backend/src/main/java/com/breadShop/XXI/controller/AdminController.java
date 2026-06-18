package com.breadShop.XXI.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.breadShop.XXI.dto.ApiResponse;
import com.breadShop.XXI.dto.customer.CustomerResponse;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.dto.product.ProductRequest;
import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.mapper.ProductMapper;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.AuditLogService;
import com.breadShop.XXI.service.OrderService;
import com.breadShop.XXI.service.ProductService;

/**
 * [Claude] AdminController — endpoints ที่เฉพาะ ADMIN เท่านั้นเข้าได้
 * path: /api/v1/admin/**
 * security: ถูกล็อกด้วย SecurityConfig (ROLE_ADMIN)
 *
 * ทุก endpoint คืน ApiResponse<T> เพื่อให้ format ตรงกับ standard ของโปรเจกต์
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final ProductService  productService;
    private final OrderService    orderService;
    private final UserRepository  userRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;

    public AdminController(ProductService productService, OrderService orderService,
                           UserRepository userRepository, OrderRepository orderRepository,
                           AuditLogService auditLogService) {
        this.productService  = productService;
        this.orderService    = orderService;
        this.userRepository  = userRepository;
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin user not found in DB"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRODUCTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * POST /api/v1/admin/add-products
     * เพิ่มสินค้าใหม่ — รับ multipart/form-data (รองรับ upload รูปภาพ)
     * คืน ProductResponse แทน Product entity เพื่อไม่ expose field ภายใน
     */
    @PostMapping(value = "/add-products", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @ModelAttribute ProductRequest request) throws IOException {

        Product product = productService.createProduct(request);
        auditLogService.log(currentUser(), "CREATE", "Product",
                String.valueOf(product.getId()), null, product.getName(), "Add product: " + product.getName());
        return ResponseEntity.ok(ApiResponse.ok("เพิ่มสินค้าสำเร็จ", ProductMapper.toResponse(product)));
    }

    /**
     * GET /api/v1/admin/products
     * ดึงสินค้าทั้งหมด — เฉพาะ admin (ถ้า user ปกติใช้ /api/v1/products แทน)
     */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> responses = productService.getAllProducts().stream()
                .map(ProductMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    /**
     * GET /api/v1/admin/get-product-by-id/{id}
     * ดึงสินค้าตาม ID สำหรับหน้า edit product
     */
    @GetMapping("/get-product-by-id/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.ok(ProductMapper.toResponse(product)));
    }

    /**
     * PUT /api/v1/admin/products/{id}
     * แก้ไขสินค้า — รับ multipart/form-data (อัปโหลดรูปใหม่ได้)
     * ถ้าไม่ส่ง image field มาจะใช้รูปเดิม
     */
    @PutMapping(value = "/products/{id}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @ModelAttribute ProductRequest request) throws IOException {

        Product product = productService.updateProduct(id, request);
        auditLogService.log(currentUser(), "UPDATE", "Product",
                String.valueOf(id), null, product.getName(), "Update product: " + product.getName());
        return ResponseEntity.ok(ApiResponse.ok("แก้ไขสินค้าสำเร็จ", ProductMapper.toResponse(product)));
    }

    /**
     * DELETE /api/v1/admin/{id}
     * ลบสินค้า — ลบรูปภาพออกจาก disk ด้วย (ดูใน ProductService.deleteProduct)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        auditLogService.log(currentUser(), "DELETE", "Product",
                String.valueOf(id), null, null, "Delete product id: " + id);
        return ResponseEntity.ok(ApiResponse.ok("ลบสินค้าสำเร็จ"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  ORDERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/admin/orders
     * ดึง order ทั้งหมดในระบบ
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAll()));
    }

    /**
     * PATCH /api/v1/admin/{id}/status?status=SHIPPED&trackingNo=TH123456
     * อัปเดตสถานะ order และ tracking number
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Integer id,
            @RequestParam OrderStatus status,
            @RequestParam(required = false) String trackingNo) {

        OrderResponse order = orderService.updateStatus(id, status, trackingNo);
        auditLogService.log(currentUser(), "CHANGE_ORDER_STATUS", "Order",
                String.valueOf(id), null, status.name(),
                "Order #" + order.getOrderNo() + " → " + status.name());
        return ResponseEntity.ok(ApiResponse.ok("อัปเดตสถานะสำเร็จ", order));
    }

    // ═══════════════════════════════════════════════════════════════
    //  CUSTOMERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/admin/customers
     * รายชื่อ user ทั้งหมด พร้อมจำนวน order ที่เคยสั่ง
     */
    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAllCustomers() {
        // โหลด order count ทุก user ในคำสั่งเดียว แทนการยิง N queries ใน loop (N+1 fix)
        Map<Integer, Long> orderCountByUser = orderRepository.countOrdersGroupByUser()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (Long) row[1]
                ));

        List<CustomerResponse> customers = userRepository.findAll().stream()
                .map(user -> new CustomerResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole(),
                        user.getProvider() != null ? user.getProvider() : "credentials",
                        user.getCreatedAt(),
                        orderCountByUser.getOrDefault(user.getId(), 0L).intValue(),
                        user.isActive()
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(customers));
    }

    /**
     * PATCH /api/v1/admin/customers/{id}/role?role=ADMIN
     * เปลี่ยน role ของ user (USER ↔ ADMIN)
     */
    @PatchMapping("/customers/{id}/role")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable Integer id,
            @RequestParam String role) {

        var user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String normalized = role.toUpperCase();
        if (!normalized.equals("USER") && !normalized.equals("ADMIN")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("role ต้องเป็น USER หรือ ADMIN เท่านั้น"));
        }

        String oldRole = user.getRole();
        user.setRole(normalized);
        userRepository.save(user);
        auditLogService.log(currentUser(), "CHANGE_ROLE", "User",
                String.valueOf(id), oldRole, normalized, "Change role for user id: " + id);
        return ResponseEntity.ok(ApiResponse.ok("อัปเดต role สำเร็จ"));
    }

    /**
     * PATCH /api/v1/admin/customers/{id}/ban
     * toggle ban/unban user — set isActive = false/true
     */
    @PatchMapping("/customers/{id}/ban")
    public ResponseEntity<ApiResponse<Void>> toggleBan(@PathVariable Integer id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean wasBanned = user.isActive();
        user.setActive(!user.isActive());
        userRepository.save(user);
        String msg = user.isActive() ? "ปลดระงับบัญชีสำเร็จ" : "ระงับบัญชีสำเร็จ";
        auditLogService.log(currentUser(), "BAN_USER", "User",
                String.valueOf(id), String.valueOf(wasBanned), String.valueOf(user.isActive()), msg + " user id: " + id);
        return ResponseEntity.ok(ApiResponse.ok(msg));
    }

    /**
     * PATCH /api/v1/admin/customers/{id}/username?username=newname
     * แก้ไข username ของ user
     */
    @PatchMapping("/customers/{id}/username")
    public ResponseEntity<ApiResponse<Void>> updateUsername(
            @PathVariable Integer id,
            @RequestParam String username) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("username ห้ามว่าง"));
        }
        var user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setUsername(username.trim());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("อัปเดต username สำเร็จ"));
    }
}
