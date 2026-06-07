import axios from "axios";

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
});

// path ที่ไม่ต้องการ login — ถ้า refresh token ล้มเหลวบน path พวกนี้ ให้ไม่ redirect
const PUBLIC_PATHS = ["/", "/login", "/register", "/forgot-password", "/verify-email", "/reset-password"];

function isPublicPage() {
  if (typeof window === "undefined") return false;
  return PUBLIC_PATHS.includes(window.location.pathname);
}

function redirectToLogin() {
  if (typeof window !== "undefined" && !isPublicPage()) {
    window.location.href = "/login";
  }
}

/**
 * [Claude] Response Interceptor 1 — ApiResponse unwrapper
 *
 * Backend ทุก endpoint คืน format: { success, message, data }
 * interceptor นี้ auto-unwrap .data ออกมาก่อน เพื่อให้ caller ยังใช้ res.data ได้เหมือนเดิม
 *
 * ก่อน: res.data = { success: true, message: "...", data: [...] }
 * หลัง: res.data = [...]   ← unwrap แล้ว
 *
 * ข้อยกเว้น:
 *   - endpoint ที่ไม่ใช้ ApiResponse (เช่น /google/callback redirect) → ผ่านตามปกติ
 *   - ถ้า backend คืน success=false → throw error ให้ interceptor error จัดการต่อ
 */
api.interceptors.response.use(
  (res) => {
    const body = res.data;
    // ตรวจว่าเป็น ApiResponse format ไหม (มี success field)
    if (body && typeof body.success === 'boolean') {
      if (!body.success) {
        // success=false → throw เพื่อให้ catch ใน caller จัดการ
        const err = new Error(body.message || 'เกิดข้อผิดพลาด');
        err.response = res;
        return Promise.reject(err);
      }
      // unwrap: แทนที่ res.data ด้วย body.data จริงๆ
      res.data = body.data;
    }
    return res;
  },
  async (err) => {
    const originalRequest = err.config;
    const status = err.response?.status;

    // 403 = ไม่มีสิทธิ์ (เช่น user เรียก admin API) — ไม่ refresh, ไม่เปลี่ยน session
    if (status === 403) {
      return Promise.reject(err);
    }

    if (status !== 401 || originalRequest._retry) {
      return Promise.reject(err);
    }

    if (originalRequest.url?.includes("/auth/refresh")) {
      redirectToLogin();
      return Promise.reject(err);
    }

    originalRequest._retry = true;

    try {
      await api.post("/api/v1/auth/refresh");
      return api(originalRequest);
    } catch (e) {
      redirectToLogin();
      return Promise.reject(e);
    }
  }
);

export default api;
