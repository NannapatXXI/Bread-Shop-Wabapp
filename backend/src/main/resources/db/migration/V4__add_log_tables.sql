-- ============================================================
-- V4__add_log_tables.sql
-- Log tables: user_activity_logs, audit_logs, order_logs
-- ============================================================

-- ─── 1. user_activity_logs ───────────────────────────────────
-- เก็บ action ของ user: LOGIN, LOGOUT, REGISTER, UPDATE_PROFILE,
--                        ADD_TO_CART, CHECKOUT, PAYMENT
CREATE TABLE IF NOT EXISTS user_activity_logs (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    user_id      INT,
    username     VARCHAR(255),
    action       VARCHAR(100)  NOT NULL,
    ip_address   VARCHAR(50),
    user_agent   TEXT,
    details      TEXT,
    status       VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS',
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES usersapp(id) ON DELETE SET NULL
);

CREATE INDEX idx_activity_user_id   ON user_activity_logs(user_id);
CREATE INDEX idx_activity_action    ON user_activity_logs(action);
CREATE INDEX idx_activity_created   ON user_activity_logs(created_at);

-- ─── 2. audit_logs ───────────────────────────────────────────
-- เก็บการกระทำของ admin/user ที่มีผลต่อข้อมูลในระบบ
-- action: CREATE, UPDATE, DELETE, CHANGE_ROLE, CHANGE_ORDER_STATUS, BAN_USER
CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    actor_id     INT,
    actor_name   VARCHAR(255),
    actor_role   VARCHAR(50),
    action       VARCHAR(100)  NOT NULL,
    entity_type  VARCHAR(100),
    entity_id    VARCHAR(100),
    old_value    TEXT,
    new_value    TEXT,
    details      TEXT,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES usersapp(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_actor_id  ON audit_logs(actor_id);
CREATE INDEX idx_audit_action    ON audit_logs(action);
CREATE INDEX idx_audit_entity    ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created   ON audit_logs(created_at);

-- ─── 3. order_logs ───────────────────────────────────────────
-- เก็บประวัติการเปลี่ยนสถานะ order ทุกครั้ง
CREATE TABLE IF NOT EXISTS order_logs (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    order_id         INT           NOT NULL,
    order_no         VARCHAR(50)   NOT NULL,
    user_id          INT,
    old_status       VARCHAR(50),
    new_status       VARCHAR(50)   NOT NULL,
    changed_by_id    INT,
    changed_by_name  VARCHAR(255),
    tracking_no      VARCHAR(100),
    note             TEXT,
    created_at       DATETIME(6)   NOT NULL,
    CONSTRAINT fk_order_log_order      FOREIGN KEY (order_id)      REFERENCES orders(id)    ON DELETE CASCADE,
    CONSTRAINT fk_order_log_user       FOREIGN KEY (user_id)       REFERENCES usersapp(id)  ON DELETE SET NULL,
    CONSTRAINT fk_order_log_changed_by FOREIGN KEY (changed_by_id) REFERENCES usersapp(id)  ON DELETE SET NULL
);

CREATE INDEX idx_order_log_order_id  ON order_logs(order_id);
CREATE INDEX idx_order_log_created   ON order_logs(created_at);
