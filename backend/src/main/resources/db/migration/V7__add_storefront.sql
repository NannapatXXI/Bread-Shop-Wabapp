-- ============================================================
-- V7__add_storefront.sql
-- หน้าร้านที่ admin ตั้งเองได้: สินค้าแนะนำ + รูปปกหน้า home
-- ============================================================

-- สินค้าแนะนำ: featured_rank ไม่เป็น NULL = แนะนำ เรียงจากเลขน้อยไปมาก
ALTER TABLE products ADD COLUMN featured_rank INT NULL;
CREATE INDEX idx_products_featured_rank ON products(featured_rank);

-- ค่าตั้งของหน้าร้าน (key/value) เช่น hero_image_url = path รูปปกในโฟลเดอร์ uploads
CREATE TABLE IF NOT EXISTS site_settings (
    setting_key   VARCHAR(100)  PRIMARY KEY,
    setting_value VARCHAR(500),
    updated_at    DATETIME(6)   NOT NULL
);
