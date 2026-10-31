package com.breadShop.XXI.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.dashboard.TopProductStat;
import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.entity.SiteSetting;
import com.breadShop.XXI.repository.OrderLineRepository;
import com.breadShop.XXI.repository.ProductRepository;
import com.breadShop.XXI.repository.SiteSettingRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // แต่ละเทสใช้ค่าตั้งต้นชุดเดียวกันไม่ครบทุกตัว
class StorefrontServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private SiteSettingRepository settingRepository;
    @Mock private ImageStorage imageStorage;

    private StorefrontService storefrontService;

    @BeforeEach
    void setUp() {
        storefrontService = new StorefrontService(productRepository, orderLineRepository, settingRepository, imageStorage);
        // ค่าตั้งต้น: ยังไม่ได้เลือก ยังไม่มียอดขาย ไม่มีสินค้า ไม่มีรูปปก
        when(productRepository.findByFeaturedRankIsNotNullOrderByFeaturedRankAsc()).thenReturn(List.of());
        when(orderLineRepository.getTopProductStats(any(), any(), anyList())).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());
        when(settingRepository.findById(StorefrontService.HERO_IMAGE)).thenReturn(Optional.empty());
    }

    private static Product product(long id, int stock) {
        Product p = new Product("P" + id, new BigDecimal("20.00"), stock, "", null, ProductCategory.BREAD, LocalDate.of(2027, 1, 1));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private void picked(Product... products) {
        for (int i = 0; i < products.length; i++) {
            products[i].setFeaturedRank(i);
        }
        when(productRepository.findByFeaturedRankIsNotNullOrderByFeaturedRankAsc()).thenReturn(List.of(products));
    }

    private void bestSellers(Product... products) {
        when(orderLineRepository.getTopProductStats(any(), any(), anyList())).thenReturn(
                java.util.Arrays.stream(products)
                        .map(p -> new TopProductStat(p.getId(), p.getName(), 10L, BigDecimal.TEN, null)).toList());
        when(productRepository.findAllById(anyList())).thenReturn(List.of(products));
    }

    private List<Long> featuredIds() {
        return storefrontService.getStorefront().featured().stream().map(ProductResponse::getId).toList();
    }

    // ───────────── สินค้าแนะนำที่ลูกค้าเห็น ─────────────

    @Test
    void adminPicks_comeFirst_inTheChosenOrder() {
        picked(product(3, 5), product(1, 5), product(2, 5));

        assertThat(featuredIds()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void soldOutPick_isSkipped_andTheNextOneMovesUp() {
        picked(product(3, 5), product(1, 0), product(2, 5), product(4, 5), product(5, 5));

        assertThat(featuredIds()).containsExactly(3L, 2L, 4L, 5L);
    }

    @Test
    void closedForSalePick_isSkipped_evenWithStock() {
        Product closed = product(1, 50);
        closed.setActive(false);
        picked(closed, product(2, 5));

        assertThat(featuredIds()).containsExactly(2L);
    }

    @Test
    void showsAtMostFour() {
        picked(product(1, 5), product(2, 5), product(3, 5), product(4, 5), product(5, 5), product(6, 5));

        assertThat(featuredIds()).hasSize(StorefrontService.FEATURED_SHOWN).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void fewerPicksThanFour_areToppedUpWithBestSellers_withoutDuplicates() {
        Product cake = product(7, 5);
        picked(cake);
        bestSellers(product(9, 5), cake, product(8, 0), product(6, 5));

        // 7 (เลือกไว้) → ขายดี 9, 6 (7 ซ้ำ ข้าม / 8 หมด ข้าม)
        assertThat(featuredIds()).containsExactly(7L, 9L, 6L);
    }

    @Test
    void noPicksAndNoSales_fallsBackToAnyInStockProduct() {
        when(productRepository.findAll()).thenReturn(List.of(product(1, 0), product(2, 3), product(3, 1)));

        assertThat(featuredIds()).containsExactly(2L, 3L);
    }

    // ───────────── รูปปก ─────────────

    @Test
    void heroImage_isNullUntilAdminSetsOne() {
        assertThat(storefrontService.getStorefront().heroImageUrl()).isNull();

        when(settingRepository.findById(StorefrontService.HERO_IMAGE))
                .thenReturn(Optional.of(new SiteSetting(StorefrontService.HERO_IMAGE, "uploads/cover.jpg")));

        assertThat(storefrontService.getStorefront().heroImageUrl()).isEqualTo("uploads/cover.jpg");
    }

    @Test
    void setHeroImage_savesNewFile_thenDeletesTheOldOne() throws Exception {
        SiteSetting setting = new SiteSetting(StorefrontService.HERO_IMAGE, "uploads/old.jpg");
        when(settingRepository.findById(StorefrontService.HERO_IMAGE)).thenReturn(Optional.of(setting));
        MockMultipartFile image = new MockMultipartFile("image", "cover.jpg", "image/jpeg", new byte[] { 1 });
        when(imageStorage.save(image)).thenReturn("uploads/new.jpg");

        assertThat(storefrontService.setHeroImage(image)).isEqualTo("uploads/new.jpg");

        assertThat(setting.getValue()).isEqualTo("uploads/new.jpg");
        verify(settingRepository).save(setting);
        verify(imageStorage).delete("uploads/old.jpg");
    }

    @Test
    void setHeroImage_firstTime_createsTheSetting() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png", new byte[] { 1 });
        when(imageStorage.save(image)).thenReturn("uploads/new.png");

        storefrontService.setHeroImage(image);

        ArgumentCaptor<SiteSetting> saved = ArgumentCaptor.forClass(SiteSetting.class);
        verify(settingRepository).save(saved.capture());
        assertThat(saved.getValue().getKey()).isEqualTo(StorefrontService.HERO_IMAGE);
        assertThat(saved.getValue().getValue()).isEqualTo("uploads/new.png");
        verify(imageStorage).delete(null);   // ไม่มีรูปเก่า — ImageStorage.delete(null) ไม่ทำอะไร
    }

    @Test
    void setHeroImage_notAnImage_keepsTheOldCover() throws Exception {
        SiteSetting setting = new SiteSetting(StorefrontService.HERO_IMAGE, "uploads/old.jpg");
        when(settingRepository.findById(StorefrontService.HERO_IMAGE)).thenReturn(Optional.of(setting));
        MockMultipartFile pdf = new MockMultipartFile("image", "menu.pdf", "application/pdf", new byte[] { 1 });
        when(imageStorage.save(pdf)).thenThrow(new IllegalArgumentException("INVALID_IMAGE_TYPE"));

        assertThatThrownBy(() -> storefrontService.setHeroImage(pdf)).hasMessage("INVALID_IMAGE_TYPE");

        assertThat(setting.getValue()).isEqualTo("uploads/old.jpg");
        verify(imageStorage, never()).delete(any());
        verify(settingRepository, never()).save(any());
    }

    @Test
    void setHeroImage_withoutFile_returns400() {
        MockMultipartFile empty = new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storefrontService.setHeroImage(empty))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void removeHeroImage_deletesSettingAndFile() {
        SiteSetting setting = new SiteSetting(StorefrontService.HERO_IMAGE, "uploads/old.jpg");
        when(settingRepository.findById(StorefrontService.HERO_IMAGE)).thenReturn(Optional.of(setting));

        storefrontService.removeHeroImage();

        verify(settingRepository).delete(setting);
        verify(imageStorage).delete("uploads/old.jpg");
    }

    // ───────────── admin ตั้งสินค้าแนะนำ ─────────────

    @Test
    void setFeatured_assignsRanksInOrder_andClearsPreviousPicks() {
        Product oldPick = product(9, 5);
        picked(oldPick);
        Product a = product(1, 5);
        Product b = product(2, 0);   // หมดก็เลือกได้ — แค่ลูกค้าไม่เห็นจนกว่าจะมีของ
        when(productRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(a, b));

        storefrontService.setFeatured(List.of(2L, 1L));

        assertThat(b.getFeaturedRank()).isZero();
        assertThat(a.getFeaturedRank()).isEqualTo(1);
        assertThat(oldPick.getFeaturedRank()).isNull();
    }

    @Test
    void setFeatured_emptyList_clearsAll() {
        Product oldPick = product(9, 5);
        picked(oldPick);
        when(productRepository.findAllById(List.of())).thenReturn(List.of());

        storefrontService.setFeatured(List.of());

        assertThat(oldPick.getFeaturedRank()).isNull();
    }

    @Test
    void setFeatured_duplicateIds_returns400() {
        assertThatThrownBy(() -> storefrontService.setFeatured(List.of(1L, 1L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void setFeatured_unknownProduct_returns404_andChangesNothing() {
        Product oldPick = product(9, 5);
        picked(oldPick);
        when(productRepository.findAllById(List.of(1L, 404L))).thenReturn(List.of(product(1, 5)));

        assertThatThrownBy(() -> storefrontService.setFeatured(List.of(1L, 404L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(oldPick.getFeaturedRank()).isZero();
    }

    @Test
    void adminView_showsPicksIncludingSoldOut() {
        picked(product(3, 0), product(1, 5));

        assertThat(storefrontService.getForAdmin().featuredProductIds()).containsExactly(3L, 1L);
    }
}
