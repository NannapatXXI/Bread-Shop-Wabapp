import api from "@/lib/api";

export const login = (credentials) => {
  return api.post("/api/v1/auth/login", credentials);
};

export const register = (data) => {
  return api.post("/api/v1/auth/register", data);
};

export const sendOTPEmail = (email) => {
  return api.post("/api/v1/auth/otp", { email });
};
export const verifyOTP = (data) => {
  return api.post("/api/v1/auth/otp/verification", data);
};
export const resetPassword = (data) => {
  return api.put("/api/v1/auth/password", data);
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
  return api.get(`/api/v1/admin/products/${id}`);
};

// [Claude] อัปเดตสินค้าตาม id
export const updateProduct = (id, formData) => {
  return api.put(`/api/v1/admin/products/${id}`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const addproduct = (formData) => {
  return api.post("/api/v1/admin/products", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}

// ── Address ──────────────────────────────────────────────────
/** ที่อยู่ของคนที่ login อยู่ — backend รู้ user จาก token */
export const getAddresses = () =>
  api.get(`/api/v1/users/me/addresses`);

// ── Promotion ────────────────────────────────────────────────
/** ส่วนลดที่จะได้จากโค้ดนี้ตามยอด — โค้ดใช้ไม่ได้ตอบ 400
 *  คืน { code, name, discountAmount } */
export const validatePromoCode = (code, amount) =>
  api.get(`/api/v1/promotions/discount`, { params: { code, amount } });

// ── Order ────────────────────────────────────────────────────
/** สร้าง order ใหม่
 *  body: { userId, addressId, promotionCode?, note?, items: [{productId, quantity}] } — ราคา/ส่วนลด/ค่าส่ง server คิดเอง */
export const createOrder = (data) =>
  api.post(`/api/v1/orders`, data);

/** ประวัติ order ของ user — userId ดึงจาก JWT ฝั่ง backend */
export const getMyOrders = () =>
  api.get(`/api/v1/orders`);

/** ยกเลิก order — userId ดึงจาก JWT ฝั่ง backend */
export const cancelOrder = (orderId) =>
  api.patch(`/api/v1/orders/${orderId}/cancel`);

// ── Admin ────────────────────────────────────────────────────
export const adminGetAllOrders    = ()         => api.get('/api/v1/admin/orders');
export const adminUpdateOrderStatus = (id, status, trackingNo) =>
  api.patch(`/api/v1/admin/orders/${id}/status`, { status, trackingNo });

export const adminGetCustomers    = ()         => api.get('/api/v1/admin/customers');

export const adminGetPromotions      = ()           => api.get('/api/v1/promotions');
export const adminCreatePromotion    = (data)       => api.post('/api/v1/promotions', data);
export const adminSetPromotionActive = (id, active) => api.patch(`/api/v1/promotions/${id}`, { active });
export const adminDeletePromotion    = (id)         => api.delete(`/api/v1/promotions/${id}`);

export const adminDeleteProduct     = (id)           => api.delete(`/api/v1/admin/products/${id}`);

export const adminUpdateUserRole    = (id, role)     => api.patch(`/api/v1/admin/customers/${id}/role`, { role });
export const adminSetCustomerActive = (id, active)   => api.patch(`/api/v1/admin/customers/${id}/active`, { active });
export const adminUpdateUsername    = (id, username) => api.patch(`/api/v1/admin/customers/${id}/username`, { username });

// ── ข้อมูลของตัวเอง ────────────────────────────────────────────
export const getMyProfile    = ()         => api.get('/api/v1/users/me');
export const updateMyProfile = (username) => api.patch('/api/v1/users/me', { username });

