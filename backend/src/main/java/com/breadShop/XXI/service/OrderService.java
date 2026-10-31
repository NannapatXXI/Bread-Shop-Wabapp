package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.Util.RequestInfo;
import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.dto.orderline.OrderLineRequest;
import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.Promotion;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.mapper.OrderLineMapper;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.PromotionRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;

/**
 * สร้าง / ดู / เปลี่ยนสถานะ / ยกเลิก order
 * ราคา ส่วนลด และค่าส่ง คิดที่ server ทั้งหมด — ไม่เชื่อตัวเลขเงินจาก client
 */
@Service
public class OrderService {

    /** ค่าส่ง — ตอนนี้ส่งฟรี ถ้าจะคิดค่าส่งให้คิดที่นี่ ห้ามรับจาก client */
    static final BigDecimal SHIPPING_FEE = BigDecimal.ZERO;

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository        orderRepository;
    private final UserRepository         userRepository;
    private final UserAddressRepository  userAddressRepository;
    private final ProductRepository      productRepository;
    private final PromotionRepository    promotionRepository;
    private final PromotionService       promotionService;
    private final NotificationService    notificationService;
    private final OrderLogService        orderLogService;
    private final UserActivityLogService activityLogService;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository,
                        UserAddressRepository userAddressRepository, ProductRepository productRepository,
                        PromotionRepository promotionRepository, PromotionService promotionService,
                        NotificationService notificationService, OrderLogService orderLogService,
                        UserActivityLogService activityLogService) {
        this.orderRepository       = orderRepository;
        this.userRepository        = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.productRepository     = productRepository;
        this.promotionRepository   = promotionRepository;
        this.promotionService      = promotionService;
        this.notificationService   = notificationService;
        this.orderLogService       = orderLogService;
        this.activityLogService    = activityLogService;
    }

    // ─── อ่าน ──────────────────────────────────────────────────────────

    /** order ของ user ใหม่สุดก่อน */
    @Transactional(readOnly = true)
    public List<OrderResponse> getByUserId(Integer userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    /** ทุก order ใหม่สุดก่อน (admin) */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    /** order ใดก็ได้ (admin) */
    @Transactional(readOnly = true)
    public OrderResponse getById(Integer id) {
        return toResponse(findOrder(id));
    }

    /**
     * order ของ user — ถ้า order ไม่ใช่ของ user นี้ตอบ 404 เหมือนไม่มีอยู่
     * @param id  id ของ order
     * @param userId id ของ user ที่ขอดู order
     * @return  OrderResponse ของ order ที่ user เป็นเจ้าของ
     */
    @Transactional(readOnly = true)
    public OrderResponse getByIdForUser(Integer id, Integer userId) {
        Order order = orderRepository.findById(id)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return toResponse(order);
    }

    // ─── สร้าง order ───────────────────────────────────────────────────

    /**
     * สร้าง order ใหม่ — คำนวณราคาที่ server ทั้งหมด ไม่เชื่อตัวเลขจาก client
     * @param request รับว่า userId, addressId, promotionCode, note, และรายการสินค้า (productId + quantity)
     * @return OrderResponse พร้อม orderNo, ราคาสินค้า, ส่วนลด, ค่าส่ง, ยอดรวม, สถานะ PENDING
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserAddress address = userAddressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (!address.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ที่อยู่นี้ไม่ใช่ของคุณ");
        }

        Order order = new Order(user, newOrderNo(), address);   // snapshot ที่อยู่ไว้ใน order
        order.setNote(request.getNote());
        order.setShippingFee(SHIPPING_FEE);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderLineRequest item : request.getItems()) {
            subtotal = subtotal.add(addLine(order, item));
        }
        order.setSubtotal(subtotal);

        BigDecimal discount = applyPromotion(order, request.getPromotionCode(), subtotal);
        order.setDiscountAmount(discount);
        order.setTotalAmount(subtotal.subtract(discount).add(SHIPPING_FEE).max(BigDecimal.ZERO));

        Order saved = orderRepository.save(order);
        activityLogService.logSuccess(user, "CHECKOUT", RequestInfo.currentClientIp(), "",
                "สั่งซื้อ order " + saved.getOrderNo() + " ยอด ฿" + saved.getTotalAmount());
        return toResponse(saved);
    }

    /**
     * เพิ่มแถวสินค้าใน order — ล็อกแถวสินค้าเพื่อป้องกันขายเกินสต็อก
     * @param order คำสั่งซื้อที่กำลังสร้าง
     * @param item รายการสินค้า (productId + quantity)
     * @return ราคาสินค้าในแถวนี้ (product.price * quantity)
     */
    private BigDecimal addLine(Order order, OrderLineRequest item) {
        // ล็อกแถวสินค้า — 2 คำสั่งซื้อพร้อมกันจะไม่ขายเกินสต็อก
        Product product = productRepository.findByIdWithLock(item.getProductId().longValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + item.getProductId()));

        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "สินค้า " + product.getName() + " ปิดการขายอยู่");
        }
        if (product.getStock() < item.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock for: " + product.getName());
        }

        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        // เก็บชื่อและราคาตอนสั่งไว้ใน line — แก้ราคาสินค้าทีหลังไม่กระทบ order เก่า
        OrderLine line = new OrderLine(order, product, product.getName(), product.getPrice(), item.getQuantity(), lineTotal);
        line.setDiscountAmount(BigDecimal.ZERO);
        order.getOrderLines().add(line);

        product.setStock(product.getStock() - item.getQuantity());
        productRepository.save(product);
        return lineTotal;
    }

   /**
    * คำนวณส่วนลดจาก promotion code — ถ้าไม่ถูกต้องหรือหมดอายุ  400
    * @param order คำสั่งซื้อที่กำลังสร้าง
    * @param code รหัสโปรโมชั่นจากลูกค้า
    * @param subtotal ราคาสินค้ารวมก่อนส่วนลด
    * @return จำนวนเงินส่วนลดที่ใช้ได้ (0 ถ้าไม่มี code)
    */
    private BigDecimal applyPromotion(Order order, String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        Promotion promotion = promotionRepository.findByCodeAndIsActiveTrueAndExpiredAtAfter(code, LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid promotion code"));

        order.setPromotion(promotion);
        order.setPromotionCode(promotion.getCode());
        promotionService.incrementUsedCount(promotion.getId());
        return promotionService.calculateDiscount(promotion, subtotal);
    }

   /**
    * สร้าง orderNo ใหม่ — รูปแบบ ORD-yyyyMMddHHmmss-XXXXXX (X เป็นตัวเลข/ตัวอักษรสุ่ม)
    * @return orderNo ใหม่
    */
    private String newOrderNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return "ORD-" + LocalDateTime.now().format(ORDER_NO_TIME) + "-" + suffix;
    }

    // ─── เปลี่ยนสถานะ ──────────────────────────────────────────────────

    /** admin เปลี่ยนสถานะ → แจ้งเตือนลูกค้า + บันทึกประวัติ */
    @Transactional
    public OrderResponse updateStatus(Integer id, OrderStatus status, String trackingNo) {
        Order order = findOrder(id);
        String oldStatus = order.getStatus().name();
        order.setStatus(status);
        if (trackingNo != null) {
            order.setTrackingNo(trackingNo);
        }
        Order saved = orderRepository.save(order);

        notificationService.createAndPush(saved, status);
        orderLogService.log(saved, oldStatus, status.name(), null, trackingNo, null);
        return toResponse(saved);
    }

    /** ลูกค้ายกเลิกได้เฉพาะ order ของตัวเองที่ยัง PENDING — คืนสต็อกให้ */
    @Transactional
    public OrderResponse cancelOrder(Integer orderId, Integer userId) {
        Order order = findOrder(orderId);

        if (!order.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์ยกเลิก order นี้");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ไม่สามารถยกเลิกได้ เนื่องจากออเดอร์อยู่ในสถานะ \"" + order.getStatus().name() + "\" แล้ว");
        }

        for (OrderLine line : order.getOrderLines()) {
            Product product = line.getProduct();
            product.setStock(product.getStock() + line.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        orderLogService.log(saved, OrderStatus.PENDING.name(), OrderStatus.CANCELLED.name(), null, null, "ยกเลิกโดยลูกค้า");
        activityLogService.logSuccess(order.getUser(), "CANCEL_ORDER", RequestInfo.currentClientIp(), "",
                "ยกเลิก order " + order.getOrderNo());
        return toResponse(saved);
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private Order findOrder(Integer id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private OrderResponse toResponse(Order o) {
        List<OrderLineResponse> lines = o.getOrderLines().stream().map(OrderLineMapper::toResponse).toList();

        return new OrderResponse(
                o.getId(), o.getOrderNo(), o.getUser().getId(),
                o.getShippingName(), o.getShippingPhone(), o.getShippingAddress(), o.getShippingProvince(),
                o.getShippingDistrict(), o.getShippingSubdistrict(), o.getShippingPostcode(),
                o.getSubtotal(), o.getDiscountAmount(), o.getShippingFee(), o.getTotalAmount(),
                o.getPromotionCode(), o.getStatus(), o.getTrackingNo(), o.getNote(),
                lines, o.getCreatedAt());
    }
}
