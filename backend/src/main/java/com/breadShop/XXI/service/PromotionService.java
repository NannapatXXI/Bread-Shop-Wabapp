package com.breadShop.XXI.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.promotion.PromotionRequest;
import com.breadShop.XXI.dto.promotion.PromotionResponse;
import com.breadShop.XXI.dto.promotion.PromotionValidateResponse;
import com.breadShop.XXI.entity.Promotion;
import com.breadShop.XXI.entity.Promotion.DiscountType;
import com.breadShop.XXI.repository.PromotionRepository;

// บริการสำหรับจัดการข้อมูลโปรโมชั่น เช่น สร้าง, อ่าน, ตรวจสอบความถูกต้อง และเพิ่มจำนวนการใช้งานของโปรโมชั่น | reviewed by peak
@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    /**
     * ดึงข้อมูลโปรโมชั่นทั้งหมดจากฐานข้อมูลและแปลงเป็น PromotionResponse DTO เพื่อส่งกลับไปยัง client ที่จำกัดข้อมูลมาแล้ว
     * @return รายการโปรโมชั่นทั้งหมดในรูปแบบ PromotionResponse DTO List
     */
    public List<PromotionResponse> getAll() {
        return promotionRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * สร้างโปรโมชั่นใหม่โดยรับข้อมูลจาก PromotionRequest DTO และตรวจสอบความถูกต้องของข้อมูล เช่น ตรวจสอบว่า code ไม่ซ้ำกับโปรโมชั่นที่มีอยู่แล้ว หากข้อมูลถูกต้องจะทำการบันทึกโปรโมชั่นใหม่ลงในฐานข้อมูลและส่งกลับเป็น PromotionResponse DTO ที่มีข้อมูลของโปรโมชั่นที่ถูกสร้างขึ้น
     * @param request ข้อมูลโปรโมชั่นใหม่ที่รับมาจาก client ในรูปแบบ PromotionRequest DTO ซึ่งประกอบด้วยข้อมูลต่างๆ เช่น code, name, discountType, discountValue, startedAt, expiredAt, minOrderAmount, maxDiscount และ usageLimit
     * @return ข้อมูลโปรโมชั่นที่ถูกสร้างขึ้นในรูปแบบ PromotionResponse DTO ซึ่งประกอบด้วยข้อมูลต่างๆ เช่น id, code, name, discountType, discountValue, minOrderAmount, maxDiscount, usageLimit, usedCount, startedAt, expiredAt และ isActive
     */
    public PromotionResponse create(PromotionRequest request) {
        if (promotionRepository.findByCode(request.getCode()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Promotion code already exists");
        }

        Promotion promotion = new Promotion(
                request.getCode(),
                request.getName(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getStartedAt(),
                request.getExpiredAt()
        );
        promotion.setMinOrderAmount(request.getMinOrderAmount());
        promotion.setMaxDiscount(request.getMaxDiscount());
        promotion.setUsageLimit(request.getUsageLimit());

        return toResponse(promotionRepository.save(promotion));
    }

    /**
     * ตรวจสอบความถูกต้องของรหัสโปรโมชั่นที่ลูกค้าป้อนเข้ามา โดยจะตรวจสอบว่าโปรโมชั่นนั้นมีอยู่จริงและยังไม่หมดอายุหรือไม่ จากนั้นจะตรวจสอบเงื่อนไขต่างๆ เช่น ยอดสั่งซื้อขั้นต่ำและจำนวนการใช้งานที่จำกัด หากโปรโมชั่นถูกต้องและตรงตามเงื่อนไข จะคำนวณส่วนลดที่ลูกค้าจะได้รับจากโปรโมชั่นนั้นและส่งกลับเป็น PromotionValidateResponse DTO ที่ประกอบด้วยข้อมูลของโปรโมชั่นและจำนวนส่วนลดที่คำนวณได้
     * @param code รหัสโปรโมชั่นที่ลูกค้าป้อนเข้ามาเพื่อตรวจสอบความถูกต้องและคำนวณส่วนลด
     * @param orderAmount ยอดสั่งซื้อของลูกค้าที่จะใช้ในการตรวจสอบเงื่อนไขต่างๆ เช่น ยอดสั่งซื้อขั้นต่ำและใช้ในการคำนวณส่วนลดที่ลูกค้าจะได้รับจากโปรโมชั่นนั้น
     * @return ข้อมูลของโปรโมชั่นและจำนวนส่วนลดที่คำนวณได้ในรูปแบบ PromotionValidateResponse DTO ซึ่งประกอบด้วยข้อมูลต่างๆ เช่น code, name และ discountAmount ที่ลูกค้าจะได้รับจากโปรโมชั่นนั้น
     */
    public PromotionValidateResponse validate(String code, BigDecimal orderAmount) {

        // 1. เช็คว่า code มีอยู่จริงไหม
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "โค้ดส่วนลดไม่ถูกต้อง"));

        // 2. เช็คว่าถูกปิดใช้งานไหม
        if (!promotion.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "โค้ดนี้ถูกปิดใช้งานแล้ว");
        }

        // 3. เช็คว่าหมดอายุยัง
        if (promotion.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "โค้ดนี้หมดอายุแล้ว");
        }

        // เช็คยอดขั้นต่ำ
        if (promotion.getMinOrderAmount() != null &&
                orderAmount.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order amount is below minimum: " + promotion.getMinOrderAmount());
        }

        // เช็ค usage limit
        if (promotion.getUsageLimit() != null &&
                promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Promotion code has reached usage limit");
        }

        BigDecimal discount = calculateDiscount(promotion, orderAmount);

        return new PromotionValidateResponse(
                promotion.getCode(),
                promotion.getName(),
                discount
        );
    }

   /**
    * คำนวณส่วนลดที่ลูกค้าจะได้รับจากโปรโมชั่นนั้น โดยจะตรวจสอบประเภทของส่วนลด (เช่น FIXED หรือ PERCENT) 
    * @param promotion โปรโมชั่นที่ต้องการคำนวณส่วนลด ซึ่งประกอบด้วยข้อมูลต่างๆ เช่น discountType, discountValue และ maxDiscount ที่ใช้ในการคำนวณส่วนลด
    * @param orderAmount ยอดสั่งซื้อของลูกค้าที่จะใช้ในการคำนวณส่วนลด โดยจะใช้ยอดสั่งซื้อในการคำนวณส่วนลด
    * @return จำนวนส่วนลดที่ลูกค้าจะได้รับจากโปรโมชั่นนั้นในรูปแบบ BigDecimal 
    */
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal orderAmount) {
        BigDecimal discount;

        if (promotion.getDiscountType() == DiscountType.FIXED) {
            discount = promotion.getDiscountValue();
        } else {
            // PERCENT
            discount = orderAmount
                    .multiply(promotion.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // ไม่เกิน maxDiscount
            if (promotion.getMaxDiscount() != null &&
                    discount.compareTo(promotion.getMaxDiscount()) > 0) {
                discount = promotion.getMaxDiscount();
            }
        }
        return discount;
    }

    /**
     * เพิ่มจำนวนการใช้งานของโปรโมชั่นที่ถูกใช้ไปแล้ว โดยจะค้นหาโปรโมชั่นจากฐานข้อมูลโดยใช้ promotionId ที่ระบุเข้ามา หากพบโปรโมชั่นดังกล่าว จะทำการเพิ่มค่า usedCount ขึ้น 1 และบันทึกการเปลี่ยนแปลงกลับไปยังฐานข้อมูล เพื่อให้สามารถติดตามจำนวนครั้งที่โปรโมชั่นถูกใช้งานได้อย่างถูกต้อง
     * @param promotionId ID ของโปรโมชั่นที่ต้องการเพิ่มจำนวนการใช้งาน โดยจะใช้ ID นี้ในการค้นหาโปรโมชั่นจากฐานข้อมูลและทำการอัปเดตค่า usedCount ของโปรโมชั่นนั้น
     */
    @Transactional
    public void incrementUsedCount(Integer promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Promotion not found"));
        promotion.setUsedCount(promotion.getUsedCount() + 1);
        promotionRepository.save(promotion);
    }
    @Transactional
    public void delete(Integer id) {
        if (!promotionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found");
        }
        promotionRepository.deleteById(id);
    }

    /**
     *  เอาไว้ เปิด/ปิด การใช้งานของโปรโมชั่น
     * @param id เอา id ของโปรโมชั่นที่ต้องการ เปิด/ปิด
     * @return ส่งข้อมูลของ โปรโมชั่นออกไปเป็น  PromotionResponse
     */
    @Transactional
    public PromotionResponse toggle(Integer id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Promotion not found"));
        promotion.setIsActive(!promotion.getIsActive());
        return toResponse(promotionRepository.save(promotion));
    }

    /**
     * แปลง Promotion Entity เป็น PromotionResponse DTO เพื่อจำกัดข้อมูลที่ส่งกลับไปยัง client
     * @param p Promotion Entity ที่ต้องการแปลง
     * @return PromotionResponse DTO ที่มีข้อมูลเฉพาะที่จำเป็นสำหรับ client
     */
    private PromotionResponse toResponse(Promotion p) {
        return new PromotionResponse(
                p.getId(), p.getCode(), p.getName(),
                p.getDiscountType(), p.getDiscountValue(),
                p.getMinOrderAmount(), p.getMaxDiscount(),
                p.getUsageLimit(), p.getUsedCount(),
                p.getStartedAt(), p.getExpiredAt(), p.getIsActive()
        );
    }
}