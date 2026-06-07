import api from "@/lib/api";

export const getMe = () => {
  return api.get("/api/v1/auth/me");
};
export const profile = () => {
  return api.get("/api/v1/auth/profile");
};
export const login = (credentials) => {
  return api.post("/api/v1/auth/login", credentials);
};

export const register = (data) => {
  return api.post("/api/v1/auth/register", data);
};

export const sendOTPEmail = (email) => {
  return api.post("/api/v1/auth/send-OTP-mail", { email });
};
export const verifyOTP = (data) => {
  return api.post("/api/v1/auth/verify-otp", data);
};
export const resetPassword = (data) => {
  return api.post("/api/v1/auth/reset-password", data);
};

/** รายการสินค้าสำหรับลูกค้า (login แล้วใครก็ได้) */
export const getProducts = () => {
  return api.get("/api/v1/products");
};

/** รายการสินค้าสำหรับ admin เท่านั้น */
export const getAdminProducts = () => {
  return api.get("/api/v1/admin/products");
};

/** @deprecated ใช้ getProducts หรือ getAdminProducts แทน */
export const getproduct = getAdminProducts;

export const getorders = () => {
  return api.get("/api/v1/admin/orders");
};



// [Claude] ดึงสินค้าตาม id สำหรับหน้า edit
export const getProductById = (id) => {
  return api.get(`/api/v1/admin/get-product-by-id/${id}`);
};

// [Claude] อัปเดตสินค้าตาม id
export const updateProduct = (id, formData) => {
  return api.put(`/api/v1/admin/products/${id}`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const addproduct = (formData) => {
  return api.post("/api/v1/admin/add-products", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}

// ── Address ──────────────────────────────────────────────────
export const getAddresses = (userId) =>
  api.get(`/api/users/${userId}/addresses`);

// ── Promotion ────────────────────────────────────────────────
/** ตรวจโค้ดส่วนลด + คำนวณส่วนลดตามยอด
 *  คืน { code, name, discountAmount } */
export const validatePromoCode = (code, amount) =>
  api.get(`/api/promotions/validate`, { params: { code, amount } });

// ── Order ────────────────────────────────────────────────────
/** สร้าง order ใหม่
 *  body: { userId, addressId, promotionCode?, shippingFee, note?, items: [{productId, quantity}] } */
export const createOrder = (data) =>
  api.post(`/api/orders`, data);

/** ประวัติ order ของ user */
export const getMyOrders = (userId) =>
  api.get(`/api/orders`, { params: { userId } });

/** ยกเลิก order — ได้เฉพาะ status PENDING */
export const cancelOrder = (orderId, userId) =>
  api.patch(`/api/orders/${orderId}/cancel`, null, { params: { userId } });

// ── Admin ────────────────────────────────────────────────────
export const adminGetAllOrders    = ()         => api.get('/api/v1/admin/orders');
export const adminUpdateOrderStatus = (id, status, trackingNo) =>
  api.patch(`/api/v1/admin/${id}/status`, null, { params: { status, ...(trackingNo ? { trackingNo } : {}) } });

export const adminGetCustomers    = ()         => api.get('/api/v1/admin/customers');

export const adminGetPromotions   = ()         => api.get('/api/promotions');
export const adminCreatePromotion = (data)     => api.post('/api/promotions', data);
export const adminTogglePromotion = (id)       => api.patch(`/api/promotions/${id}/toggle`);
export const adminDeletePromotion = (id)       => api.delete(`/api/promotions/${id}`);

export const adminUpdateUserRole  = (id, role)     => api.patch(`/api/v1/admin/customers/${id}/role`, null, { params: { role } });
export const adminToggleBan       = (id)            => api.patch(`/api/v1/admin/customers/${id}/ban`);
export const adminUpdateUsername  = (id, username)  => api.patch(`/api/v1/admin/customers/${id}/username`, null, { params: { username } });

