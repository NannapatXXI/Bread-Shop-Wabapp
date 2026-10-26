-- ============================================================
-- V6__drop_api_request_logs.sql
-- เลิกเก็บ HTTP request ลง DB — ดู request/latency/error ใน Grafana (Prometheus) แทน
-- ============================================================

DROP TABLE IF EXISTS api_request_logs;
