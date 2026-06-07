package com.breadShop.XXI.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.product.ProductRequest;
import com.breadShop.XXI.entity.Product;
import com.breadShop.XXI.entity.ProductCategory;
import com.breadShop.XXI.repository.ProductRepository;


/// บริการสำหรับจัดการข้อมูลสินค้า เช่น สร้าง, อ่าน, อัปเดต, ลบ และนับจำนวนสินค้าตามเงื่อนไขต่างๆ | reviewed by peak
@Service
public class ProductService {

    private final ProductRepository productRepository;

    private static final String UPLOAD_DIR =
        Paths.get(System.getProperty("user.dir")).getParent().resolve("uploads").toString();


   
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

   /**
    * สร้างสินค้าใหม่พร้อมบันทึกรูปภาพที่อัปโหลดไปยังโฟลเดอร์ uploads และเก็บ URL ของรูปในฐานข้อมูล
    * @param request ข้อมูลสินค้าใหม่ที่รับมาจาก client รวมถึงไฟล์รูปภาพ
    * @return ข้อมูลสินค้าใหม่ที่ถูกบันทึกในฐานข้อมูลพร้อม URL ของรูปภาพ
    * @throws IOException หากเกิดข้อผิดพลาดในการบันทึกรูปภาพ
    */
   public Product createProduct(ProductRequest request) throws IOException {

    MultipartFile image = request.getImage();

    String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
    File destinationFile = new File(UPLOAD_DIR, fileName);
    image.transferTo(destinationFile);

    Product product = new Product();
    product.setName(request.getName());
    product.setPrice(request.getPrice());
    product.setStock(request.getStock());
    product.setDescription(request.getDescription());
    product.setCategory(request.getCategory());
    product.setExpiryDate(request.getExpiryDate());
    product.setImageUrl("uploads/" + fileName);

    return productRepository.save(product);
}

    /**
     * ดึงข้อมูลสินค้าทั้งหมดจากฐานข้อมูล
     * @return รายการสินค้าทั้งหมดในรูปแบบ List<Product>
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }



    /**
     * ค้นหาสินค้าตาม ID หากไม่พบจะโยนข้อผิดพลาด 404 Not Found
     * @param id ID ของสินค้าที่ต้องการค้นหา
     * @return ข้อมูลสินค้าในรูปแบบ Product Entity หากพบ, หรือโยนข้อผิดพลาดหากไม่พบ
     */
    public Product getProductById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found Product ID")); 
    }



    /**
     * อัปเดตข้อมูลสินค้าโดย ID หากไม่พบจะโยนข้อผิดพลาด 404 Not Found และหากมีรูปใหม่ส่งมา จะลบรูปเก่าแล้วบันทึกรูปใหม่แทน
     * @param id ID ของสินค้าที่ต้องการอัปเดต
     * @param request ข้อมูลสินค้าใหม่ที่รับมาจาก client รวมถึงไฟล์รูปภาพ (ถ้ามี)
     * @return ข้อมูลสินค้าใหม่ที่ถูกอัปเดตในฐานข้อมูลพร้อม URL ของรูปภาพ (ถ้ามี)
     * @throws IOException หากเกิดข้อผิดพลาดในการบันทึกรูปภาพใหม่หรือการลบรูปภาพเก่า
     */
    public Product updateProduct(Long id, ProductRequest request) throws IOException {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setExpiryDate(request.getExpiryDate());

        // ถ้ามีรูปใหม่ส่งมา → ลบรูปเก่า แล้ว save รูปใหม่
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            if (product.getImageUrl() != null) {
                try {
                    Files.deleteIfExists(Paths.get(System.getProperty("user.dir")).resolve(product.getImageUrl()));
                } catch (IOException e) {
                    System.out.println("Delete old image failed: " + e.getMessage());
                }
            }
            String fileName = UUID.randomUUID() + "_" + request.getImage().getOriginalFilename();
            File dest = new File(UPLOAD_DIR, fileName);
            request.getImage().transferTo(dest);
            product.setImageUrl("uploads/" + fileName);
        }

        return productRepository.save(product);
    }

    /**
     * ลบสินค้าตาม ID หากไม่พบจะโยนข้อผิดพลาด 404 Not Found และหากสินค้ามีรูปภาพที่เก็บไว้ จะลบรูปภาพนั้นด้วย
     * @param id ID ของสินค้าที่ต้องการลบ
     */
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    
        if (product.getImageUrl() != null) {
            try {
                Path filePath = Paths.get(System.getProperty("user.dir"))
                        .resolve(product.getImageUrl());
    
                Files.deleteIfExists(filePath);
    
            } catch (IOException e) {
                System.out.println("Image delete failed: " + e.getMessage());
            }
        }
    
        productRepository.delete(product);
    }
   /**
    * นับจำนวนสินค้าตามหมวดหมู่ที่กำหนด
    * @param category ประเภทของสินค้าที่ต้องการนับ
    * @return  จำนวนสินค้าที่อยู่ในหมวดหมู่ที่กำหนด
    */
    public Long countProductCategory(ProductCategory  category) {
        Long count = productRepository.countByCategory(category);
        System.out.println("Total products: " + count);
        return  count;
       
    }
    /**
     * นับจำนวนสินค้าทั้งหมดในฐานข้อมูล
     * @return จำนวนสินค้าทั้งหมดในฐานข้อมูล
     */
    public Long countAllProducts() {
        Long count = productRepository.count();
        System.out.println("Total products: " + count);
        return  count;
       
    }

    /**
     * นับจำนวนสินค้าที่หมดอายุแล้ว (expiryDate ก่อนวันที่ปัจจุบัน)
     * @return จำนวนสินค้าที่หมดอายุแล้วในฐานข้อมูล
     */
    public Long countProductsByExpiryDate() {
        Long count = productRepository.countByExpiryDateBefore(java.time.LocalDate.now());
        System.out.println("Total expired products: " + count);
        return  count;
       
    }
    /**
     * นับจำนวนสินค้าที่มีจำนวนในสต็อกมากกว่า stock ที่กำหนด
     * @param stock จำนวนสินค้าที่ต้องการตรวจสอบ (นับสินค้าที่มี stock มากกว่า จำนวนนี้)
     * @return จำนวนสินค้าที่มีจำนวนในสต็อกมากกว่า stock ที่กำหนด
     */
    public Long countProductsByStock(int stock) {
        Long count = productRepository.countByStockGreaterThan(stock);
        System.out.println("Total low stock products: " + count);
        return  count;
       
    }

  
}
