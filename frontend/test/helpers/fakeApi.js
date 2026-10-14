import api from '@/lib/api';

/**
 * แทนที่ชั้น network ของ axios — หน้าเว็บ, service และ interceptor ยังเป็นของจริงทั้งหมด
 * handler(config) คืน { status, body } ; คืนรายการ request ที่ถูกส่งออกไป (body parse เป็น object แล้ว)
 */
export function fakeApi(handler) {
  const calls = [];
  api.defaults.adapter = async (config) => {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
    calls.push({ method: config.method, url: config.url, body, ...(config.params ? { params: config.params } : {}) });
    const { status = 200, body: resBody } = (await handler(config, body)) ?? {};
    const response = { data: resBody, status, statusText: String(status), headers: {}, config, request: {} };
    if (status >= 200 && status < 300) return response;
    const err = new Error(`HTTP ${status}`);
    err.config = config;
    err.response = response;
    throw err;
  };
  return calls;
}

export const ok = (data, message = 'success') => ({ status: 200, body: { success: true, message, data } });
export const fail = (status, message) => ({ status, body: { success: false, message, data: null } });
