package com.breadShop.XXI.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.dashboard.TopProductStat;
import com.breadShop.XXI.dto.storefront.AdminStorefrontResponse;
import com.breadShop.XXI.dto.storefront.StorefrontResponse;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.SiteSetting;
import com.breadShop.XXI.mapper.ProductMapper;
import com.breadShop.XXI.repository.OrderLineRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.SiteSettingRepository;

/**
 * หน้าร้านที่ admin ตั้งเองได้: รูปปกหน้า home + สินค้าแนะนำ
 *
 * สินค้าแนะนำที่ลูกค้าเห็น (สูงสุด FEATURED_SHOWN ชิ้น เฉพาะที่เปิดขายและยังมีของ):
 *   ที่ admin เลือก (ตามลำดับ) → ขายดีจริง 30 วันล่าสุด → สินค้าอื่นที่มีของ
 *   ตัวที่หมดถูกข้าม ตัวถัดไปเลื่อนขึ้นมา — ร้านใหม่ที่ยังไม่มียอดขายก็ไม่ว่าง
 */
@Service
public class StorefrontService {

    static final int FEATURED_SHOWN = 4;
    static final String HERO_IMAGE = "hero_image_url";

    private final ProductRepository     productRepository;
    private final OrderLineRepository   orderLineRepository;
    private final SiteSettingRepository settingRepository;
    private final ImageStorage          imageStorage;

    public StorefrontService(ProductRepository productRepository, OrderLineRepository orderLineRepository,
                             SiteSettingRepository settingRepository, ImageStorage imageStorage) {
        this.productRepository   = productRepository;
        this.orderLineRepository = orderLineRepository;
        this.settingRepository   = settingRepository;
        this.imageStorage        = imageStorage;
    }

    // ─── ลูกค้า ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StorefrontResponse getStorefront() {
        List<Product> featured = Stream.of(adminPicks(), bestSellers(), allProducts())
                .flatMap(List::stream)
                .filter(p -> p.isActive() && p.getStock() != null && p.getStock() > 0)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (first, dup) -> first, LinkedHashMap::new))
                .values().stream()
                .limit(FEATURED_SHOWN)
                .toList();
        return new StorefrontResponse(heroImageUrl(), featured.stream().map(ProductMapper::toResponse).toList());
    }

    // ─── admin ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminStorefrontResponse getForAdmin() {
        return new AdminStorefrontResponse(heroImageUrl(), adminPicks().stream().map(Product::getId).toList());
    }

    /** ตั้งสินค้าแนะนำใหม่ทั้งชุดตามลำดับที่ส่งมา — id ซ้ำ → 400, ไม่มีสินค้านี้ → 404 */
    @Transactional
    public void setFeatured(List<Long> productIds) {
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "มีสินค้าซ้ำในรายการ");
        }
        Map<Long, Product> found = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (found.size() != productIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        // ล้างผ่าน entity (ไม่ใช้ bulk UPDATE) — bulk ข้าม persistence context ทำให้ตัวที่ได้ลำดับเดิม
        // ถูกมองว่า "ไม่เปลี่ยน" และไม่ถูกเขียนกลับ จน DB ค้างเป็น NULL
        adminPicks().forEach(p -> p.setFeaturedRank(null));
        for (int rank = 0; rank < productIds.size(); rank++) {
            found.get(productIds.get(rank)).setFeaturedRank(rank);
        }
    }

    /** เปลี่ยนรูปปก — บันทึกรูปใหม่ก่อน (ชนิดไฟล์ผิดจะ throw ก่อนแตะของเดิม) แล้วค่อยลบรูปเก่า @return path รูปใหม่ */
    @Transactional
    public String setHeroImage(MultipartFile image) throws IOException {
        if (!ImageStorage.hasFile(image)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "กรุณาเลือกรูปปก");
        }
        String newUrl = imageStorage.save(image);
        SiteSetting setting = settingRepository.findById(HERO_IMAGE).orElseGet(() -> new SiteSetting(HERO_IMAGE, null));
        String oldUrl = setting.getValue();
        setting.setValue(newUrl);
        settingRepository.save(setting);
        imageStorage.delete(oldUrl);
        return newUrl;
    }

    /** กลับไปใช้รูปปกตั้งต้น (รูปสินค้า) */
    @Transactional
    public void removeHeroImage() {
        settingRepository.findById(HERO_IMAGE).ifPresent(setting -> {
            settingRepository.delete(setting);
            imageStorage.delete(setting.getValue());
        });
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private String heroImageUrl() {
        return settingRepository.findById(HERO_IMAGE).map(SiteSetting::getValue).orElse(null);
    }

    private List<Product> adminPicks() {
        return productRepository.findByFeaturedRankIsNotNullOrderByFeaturedRankAsc();
    }

    /** เรียงตามจำนวนชิ้นที่ขายได้ 30 วันล่าสุด (ไม่นับ order ที่ยกเลิก/คืนเงิน — กติกาเดียวกับ dashboard) */
    private List<Product> bestSellers() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = orderLineRepository.getTopProductStats(now.minusDays(30), now, DashboardService.EXCLUDED)
                .stream().map(TopProductStat::productId).toList();
        Map<Long, Product> byId = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private List<Product> allProducts() {
        return productRepository.findAll();
    }
}
