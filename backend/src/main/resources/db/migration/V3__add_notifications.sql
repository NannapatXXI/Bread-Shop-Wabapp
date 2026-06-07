-- V3: เพิ่มตาราง notifications สำหรับแจ้งเตือนการเปลี่ยนสถานะออเดอร์
CREATE TABLE notifications (
    id         INT           AUTO_INCREMENT PRIMARY KEY,
    user_id    INT           NOT NULL,
    order_id   INT           NOT NULL,
    order_no   VARCHAR(50)   NOT NULL,
    message    VARCHAR(500)  NOT NULL,
    new_status VARCHAR(50)   NOT NULL,
    is_read    TINYINT(1)    NOT NULL DEFAULT 0,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notification_user  FOREIGN KEY (user_id)  REFERENCES usersapp(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES orders(id)   ON DELETE CASCADE
);
