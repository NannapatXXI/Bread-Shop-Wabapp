import api from "@/lib/api";

export const getLogSummary      = ()         => api.get("/api/v1/admin/logs/summary").then(r => r.data);
export const getActivityLogs    = (params)   => api.get("/api/v1/admin/logs/activity", { params }).then(r => r.data);
export const getAuditLogs       = (params)   => api.get("/api/v1/admin/logs/audit", { params }).then(r => r.data);
export const getOrderLogs       = (params)   => api.get("/api/v1/admin/logs/orders", { params }).then(r => r.data);
