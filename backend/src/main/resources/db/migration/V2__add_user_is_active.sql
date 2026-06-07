-- V2: เพิ่ม is_active column สำหรับ ban/unban user
ALTER TABLE usersapp ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
