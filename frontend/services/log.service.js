import api from "@/lib/api";

export const getLogSummary      = ()         => api.get("/api/v1/admin/logs/summary").then(r => r.data);
export const getLogTrend        = ()         => api.get("/api/v1/admin/logs/trend").then(r => r.data);
export const getErrorTrend      = (days = 7) => api.get("/api/v1/admin/logs/error-trend", { params: { days } }).then(r => r.data);
export const getSystemLogs      = (params)   => api.get("/api/v1/admin/logs/system", { params }).then(r => r.data);
export const getActivityLogs    = (params)   => api.get("/api/v1/admin/logs/activity", { params }).then(r => r.data);
export const getAuditLogs       = (params)   => api.get("/api/v1/admin/logs/audit", { params }).then(r => r.data);
export const getOrderLogs       = (params)   => api.get("/api/v1/admin/logs/orders", { params }).then(r => r.data);
export const getApiStats        = ()         => api.get("/api/v1/admin/logs/api-stats").then(r => r.data);
export const getFailedRequests  = (params)   => api.get("/api/v1/admin/logs/failed-requests", { params }).then(r => r.data);
export const getErrorBreakdown  = ()         => api.get("/api/v1/admin/logs/error-breakdown").then(r => r.data);
