package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.order.OrderRequest;
import com.breadShop.XXI.dto.order.OrderResponse;
import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.Order;
import com.breadShop.XXI.entity.Order.OrderStatus;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.Promotion;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserAddress;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.PromotionRepository;
import com.breadShop.XXI.repository.UserAddressRepository;
import com.breadShop.XXI.repository.UserRepository;

// Service สำหรับจัดการ order ทั้งหมด ตั้งแต่การสร้าง order, ดึง order ของ user, ดึง order เดี่ยว, อัปเดตสถานะ และดึง order ทั้งหมด (สำหรับ admin) | reviewed by peak
@Service
public class OrderService {

    private final OrderRepository         orderRepository;
    private final UserRepository          userRepository;
    private final UserAddressRepository   userAddressRepository;
    private final ProductRepository       productRepository;
    private final PromotionRepository     promotionRepository;
    private final PromotionService        promotionService;
    private final NotificationService     notificationService;
    private final OrderLogService         orderLogService;
    private final UserActivityLogService  activityLogService;

    public OrderService(OrderRepository orderRepository,
                        UserRepository userRepository,
                        UserAddressRepository userAddressRepository,
                        ProductRepository productRepository,
                        PromotionRepository promotionRepository,
                        PromotionService promotionService,
                        NotificationService notificationService,
                        OrderLogService orderLogService,
                        UserActivityLogService activityLogService) {
        this.orderRepository      = orderRepository;
        this.userRepository       = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.productRepository    = productRepository;
        this.promotionRepository  = promotionRepository;
        this.promotionService     = promotionService;
        this.notificationService  = notificationService;
        this.orderLogService      = orderLogService;
        this.activityLogService   = activityLogService;
    }

    /**
     * ดึง IP ของ client จาก request header "X-Forwarded-For" หรือจาก remote address ของ request ถ้าไม่พบจะส่งกลับเป็น empty string
     * @return IP ของ client หรือ empty string
     */ 
    private String getClientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "";
            var req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        } catch (Exception e) { return ""; }
    }
    /**
     * ดึง order ทั้งหมดของ user ที่ระบุ โดยเรียงลำดับจากวันที่สร้างล่าสุดไปยังเก่าสุด และแปลงเป็น OrderResponse (DTO)
     * @param userId รหัสผู้ใช้ที่ต้องการดึง order
     * @return List ของ OrderResponse ที่เกี่ยวข้องกับผู้ใช้
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getByUserId(Integer userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    
    /**
     * ดึง order ที่ระบุ โดยถ้า order นั้นมีอยู่ จะทำการแปลงเป็น OrderResponse (DTO) และส่งกลับไป
     * @param id รหัสของ order ที่ต้องการดึง
     * @return OrderResponse ที่เกี่ยวข้องกับ order
     */
    @Transactional(readOnly = true)
    public OrderResponse getById(Integer id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));
        return toResponse(order);
    }

    // สร้าง order
    /**
     * ขั้นตอนการสร้าง order: ที่ละขั้นตอนจะมีการตรวจสอบข้อมูลและคำนวณราคาต่างๆ ดังนี้
     * 1. ดึง user จากฐานข้อมูลโดยใช้ userId ที่ได้รับมาใน request หากไม่พบจะโยนข้อผิดพลาด 404 Not Found
     * 2. ดึงที่อยู่ที่เลือกจากฐานข้อมูลโดยใช้ addressId ที่ได้รับมาใน request หากไม่พบจะโยนข้อผิดพลาด 404 Not Found
     * 3. สร้าง order ใหม่และ snapshot ที่อยู่จากข้อมูลที่ดึงมา
     * 4. สำหรับแต่ละ item ใน order request:
     *   - ดึง product จากฐานข้อมูลโดยใช้ productId หากไม่พบจะโยนข้อผิดพลาด 404 Not Found
     *  - เช็ค stock ของ product หากไม่พอจะโยนข้อผิดพลาด 400 Bad Request
     *  - คำนวณ line total โดยใช้ราคาสินค้า คูณกับจำนวน และหักส่วนลด (ถ้ามี)
     * - สร้าง order line ใหม่และเพิ่มเข้าไปใน order
     *  - ลด stock ของ product และบันทึกการเปลี่ยนแปลง
     * 5. คำนวณ subtotal ของ order โดยรวมราคาของทุก order line
     * 6. ถ้ามี promotion code ให้ตรวจสอบความถูกต้องและคำนวณส่วนลดโปรโมชั่น จากนั้นเพิ่มข้อมูลโปรโมชั่นเข้าไปใน order
     * 7. คำนวณ total ของ order โดยใช้สูตร: total = subtotal - discount + shipping fee (ถ้าส่วนลดมากกว่ายอดสินค้าให้ตีเป็น 0)
     * 8. บันทึก order ลงในฐานข้อมูลและส่งกลับข้อมูล order ในรูปแบบ OrderResponse
     * 
     * @param request
     * @return
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        // 1. ดึง user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // 2. ดึงที่อยู่ที่เลือก + ตรวจว่าเป็นของ user คนนี้ (B-5 address ownership)
        UserAddress address = userAddressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Address not found"));
        if (!address.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ที่อยู่นี้ไม่ใช่ของคุณ");
        }

        // 3. สร้าง order + snapshot ที่อยู่
        Order order = new Order(user, generateOrderNo(), address);
        order.setShippingFee(request.getShippingFee() != null
                ? request.getShippingFee() : BigDecimal.ZERO);
        order.setNote(request.getNote());

        // 4. สร้าง order lines + คำนวณ subtotal
        BigDecimal subtotal = BigDecimal.ZERO;

        for (var item : request.getItems()) {
            // Pessimistic write lock — ป้องกัน race condition ตอน 2 คำสั่งซื้อพร้อมกัน (B-4)
            Product product = productRepository.findByIdWithLock(item.getProductId().longValue())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Product not found: " + item.getProductId()));

            // เช็ค stock
            if (product.getStock() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for: " + product.getName());
            }

            BigDecimal discount = item.getDiscountAmount() != null
                    ? item.getDiscountAmount() : BigDecimal.ZERO;

            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .subtract(discount);

            OrderLine line = new OrderLine(
                    order,
                    product,
                    product.getName(),        // snapshot
                    product.getPrice(),       // snapshot
                    item.getQuantity(),
                    lineTotal
            );
            line.setDiscountAmount(discount);
            order.getOrderLines().add(line);

            subtotal = subtotal.add(lineTotal);

            // ลด stock
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }

        order.setSubtotal(subtotal);

        // 5. คำนวณส่วนลดโปรโมชั่น
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getPromotionCode() != null && !request.getPromotionCode().isBlank()) {
            Promotion promotion = promotionRepository
                    .findByCodeAndIsActiveTrueAndExpiredAtAfter(
                            request.getPromotionCode(), LocalDateTime.now())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Invalid promotion code"));

            discountAmount = promotionService.calculateDiscount(promotion, subtotal);
            order.setPromotion(promotion);
            order.setPromotionCode(promotion.getCode());
            promotionService.incrementUsedCount(promotion.getId());
        }

        order.setDiscountAmount(discountAmount);

        // 6. คำนวณ total — ถ้าส่วนลดมากกว่ายอดสินค้าให้ตีเป็น 0 (ห้ามติดลบ)
        BigDecimal total = subtotal
                .subtract(discountAmount)
                .add(order.getShippingFee())
                .max(BigDecimal.ZERO);
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        activityLogService.logSuccess(user, "CHECKOUT", getClientIp(), "",
                "สั่งซื้อ order " + saved.getOrderNo() + " ยอด ฿" + saved.getTotalAmount());
        return toResponse(saved);
    }

    // อัปเดตสถานะ
    /**
     * เอาไว้อัปเดตสถานะของ order โดยรับ id ของ order ที่ต้องการอัปเดต, สถานะใหม่ และหมายเลขติดตาม (ถ้ามี)
     * @param id
     * @param status
     * @param trackingNo
     * @return ข้อมูล order หลังจากอัปเดตในรูปแบบ OrderResponse
     */
    @Transactional
    public OrderResponse updateStatus(Integer id, OrderStatus status, String trackingNo) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));
        String oldStatus = order.getStatus().name();
        order.setStatus(status);
        if (trackingNo != null) order.setTrackingNo(trackingNo);
        Order saved = orderRepository.save(order);
        notificationService.createAndPush(saved, status);
        orderLogService.log(saved, oldStatus, status.name(), null, trackingNo, null);
        return toResponse(saved);
    }

    // generate order number
    /**
     * เอาไว้สร้างเลข order แบบง่ายๆ โดยใช้ timestamp ปัจจุบันในรูปแบบ "yyyyMMddHHmmss" 
     * แล้วต่อด้วย prefix "ORD-" เพื่อให้ดูเป็นเลข order ที่ไม่ซ้ำกันและมีความหมาย
     * @return
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "ORD-" + timestamp;
    }


    /**
     * แปลง OrderLine Entity เป็น OrderLineResponse DTO โดยดึงข้อมูลที่จำเป็นจาก OrderLine และ Product ที่เกี่ยวข้อง
     * @param ol
     * @return ข้อมูล OrderLineResponse ที่ประกอบด้วย id, productId, productName, productSku, imageUrl, unitPrice, quantity, discountAmount และ totalPrice
     */
    private OrderLineResponse toLineResponse(OrderLine ol) {
        return new OrderLineResponse(
                ol.getId(),
                ol.getProduct().getId().intValue(),
                ol.getProductName(),
                ol.getProductSku(),
                ol.getProduct().getImageUrl(),
                ol.getUnitPrice(),
                ol.getQuantity(),
                ol.getDiscountAmount(),
                ol.getTotalPrice()
        );
    }

    /**
     * แปลง Order Entity เป็น OrderResponse DTO โดยดึงข้อมูลที่จำเป็นจาก Order และ OrderLine ที่เกี่ยวข้อง
     * @param o         
     * @return ข้อมูล OrderResponse ที่ประกอบด้วย id, orderNo, userId, shippingName, shippingPhone, shippingAddress, shippingProvince, shippingDistrict, shippingSubdistrict, shippingPostcode, subtotal, discountAmount, shippingFee, totalAmount, promotionCode, status, trackingNo, note, list ของ OrderLineResponse และ createdAt
     */
    private OrderResponse toResponse(Order o) {
        List<OrderLineResponse> lines = o.getOrderLines()
                .stream()
                .map(this::toLineResponse)
                .collect(Collectors.toList());

        return new OrderResponse(
                o.getId(), o.getOrderNo(),
                o.getUser().getId(),
                o.getShippingName(), o.getShippingPhone(),
                o.getShippingAddress(), o.getShippingProvince(),
                o.getShippingDistrict(), o.getShippingSubdistrict(),
                o.getShippingPostcode(),
                o.getSubtotal(), o.getDiscountAmount(),
                o.getShippingFee(), o.getTotalAmount(),
                o.getPromotionCode(), o.getStatus(),
                o.getTrackingNo(), o.getNote(),
                lines, o.getCreatedAt()
        );
    }


    // ยกเลิก order — ได้เฉพาะ PENDING และเป็น order ของ user คนนั้น
    @Transactional
    public OrderResponse cancelOrder(Integer orderId, Integer userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์ยกเลิก order นี้");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ไม่สามารถยกเลิกได้ เนื่องจากออเดอร์อยู่ในสถานะ \"" + order.getStatus().name() + "\" แล้ว");
        }

        // คืน stock
        for (OrderLine line : order.getOrderLines()) {
            Product product = line.getProduct();
            product.setStock(product.getStock() + line.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        orderLogService.log(saved, OrderStatus.PENDING.name(), OrderStatus.CANCELLED.name(), null, null, "ยกเลิกโดยลูกค้า");
        activityLogService.logSuccess(order.getUser(), "CANCEL_ORDER", getClientIp(), "",
                "ยกเลิก order " + order.getOrderNo());
        return toResponse(saved);
    }

    // ดึง order ทั้งหมด (admin) — ใหม่สุดก่อน
    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}