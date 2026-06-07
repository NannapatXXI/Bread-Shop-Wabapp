-- ============================================================
-- V5__add_api_request_logs.sql
-- เก็บทุก HTTP request ที่เข้ามาสำหรับ API Monitoring
-- ============================================================

CREATE TABLE IF NOT EXISTS api_request_logs (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    method       VARCHAR(10)   NOT NULL,
    endpoint     VARCHAR(500)  NOT NULL,   -- normalized path เช่น /api/v1/orders/{id}
    uri          VARCHAR(1000),            -- URI จริง
    status_code  INT           NOT NULL,
    duration_ms  BIGINT        NOT NULL,
    user_email   VARCHAR(255),
    ip_address   VARCHAR(50),
    created_at   DATETIME(6)   NOT NULL
);

CREATE INDEX idx_api_log_endpoint   ON api_request_logs(endpoint);
CREATE INDEX idx_api_log_status     ON api_request_logs(status_code);
CREATE INDEX idx_api_log_created    ON api_request_logs(created_at);
