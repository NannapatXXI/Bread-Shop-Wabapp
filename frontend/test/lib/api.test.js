import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import api from '@/lib/api';

// แทนที่ชั้น network ของ axios ด้วย adapter ปลอม — interceptor ทุกตัวยังทำงานจริง
function fakeServer(handler) {
  const calls = [];
  api.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, data: config.data });
    const { status, body } = await handler(config, calls);
    const response = { data: body, status, statusText: String(status), headers: {}, config, request: {} };
    if (status >= 200 && status < 300) return response;
    const err = new Error(`HTTP ${status}`);
    err.config = config;
    err.response = response;
    throw err;
  };
  return calls;
}

const ok = (data) => ({ status: 200, body: { success: true, message: 'success', data } });

describe('lib/api interceptors', () => {
  const originalAdapter = api.defaults.adapter;
  let originalLocation;

  beforeEach(() => {
    originalLocation = window.location;
    delete window.location;
    window.location = { pathname: '/home', href: 'http://localhost:3000/home' };
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    window.location = originalLocation;
  });

  it('unwraps ApiResponse so callers read res.data directly', async () => {
    fakeServer(() => ok([{ id: 1 }]));

    const res = await api.get('/api/v1/products');

    expect(res.data).toEqual([{ id: 1 }]);
  });

  it('turns success=false into a rejected promise carrying the backend message', async () => {
    fakeServer(() => ({ status: 200, body: { success: false, message: 'โค้ดหมดอายุ', data: null } }));

    await expect(api.get('/api/promotions/validate')).rejects.toThrow('โค้ดหมดอายุ');
  });

  it('on 401 refreshes the session once and retries the original request', async () => {
    let ordersCalls = 0;
    const calls = fakeServer((config) => {
      if (config.url === '/api/v1/auth/refresh') return ok(null);
      ordersCalls += 1;
      return ordersCalls === 1 ? { status: 401, body: { success: false, message: 'กรุณาเข้าสู่ระบบ' } } : ok(['order']);
    });

    const res = await api.get('/api/orders');

    expect(res.data).toEqual(['order']);
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual([
      'get /api/orders',
      'post /api/v1/auth/refresh',
      'get /api/orders',
    ]);
  });

  it('does not loop when the retried request is still 401', async () => {
    // ถ้าโค้ดวนลูป refresh จะหยุดเองหลัง 3 ครั้ง (ตอบ 500) — เทสจะ fail ด้วยตัวเลขชัด ๆ ไม่ใช่ค้าง
    let refreshes = 0;
    const calls = fakeServer((config) => {
      if (config.url !== '/api/v1/auth/refresh') return { status: 401, body: { success: false } };
      refreshes += 1;
      return refreshes > 3 ? { status: 500, body: { success: false } } : ok(null);
    });

    await expect(api.get('/api/orders')).rejects.toBeTruthy();

    expect(calls.filter((c) => c.url === '/api/v1/auth/refresh')).toHaveLength(1);
  });

  it('several requests expiring at once share ONE refresh (backend rotates the refresh token)', async () => {
    // เหมือน backend จริง: refresh token ใช้ได้ครั้งเดียว — ครั้งที่สองด้วย cookie เดิมได้ 401
    let refreshes = 0;
    let loggedIn = false;
    const calls = fakeServer(async (config) => {
      if (config.url === '/api/v1/auth/refresh') {
        refreshes += 1;
        await new Promise((r) => setTimeout(r, 5));
        if (refreshes > 1) return { status: 401, body: { success: false, message: 'Token ไม่ถูกต้อง' } };
        loggedIn = true;
        return ok(null);
      }
      return loggedIn ? ok(config.url) : { status: 401, body: { success: false } };
    });

    const results = await Promise.all([api.get('/api/orders'), api.get('/api/v1/notifications'), api.get('/api/v1/auth/me')]);

    expect(results.map((r) => r.data)).toEqual(['/api/orders', '/api/v1/notifications', '/api/v1/auth/me']);
    expect(calls.filter((c) => c.url === '/api/v1/auth/refresh')).toHaveLength(1);
    expect(window.location.href).toBe('http://localhost:3000/home'); // ไม่ถูกเด้งไปหน้า login
  });

  it('a later expiry (after the first refresh finished) refreshes again', async () => {
    let session = 'expired';
    const calls = fakeServer((config) => {
      if (config.url === '/api/v1/auth/refresh') { session = 'ok'; return ok(null); }
      return session === 'ok' ? ok('data') : { status: 401, body: { success: false } };
    });

    await api.get('/api/orders');
    session = 'expired'; // 15 นาทีต่อมา access token หมดอีกรอบ
    await api.get('/api/orders');

    expect(calls.filter((c) => c.url === '/api/v1/auth/refresh')).toHaveLength(2);
  });

  it('does not try to refresh on 403 (logged in but not allowed)', async () => {
    const calls = fakeServer(() => ({ status: 403, body: { success: false } }));

    await expect(api.get('/api/v1/admin/orders')).rejects.toBeTruthy();

    expect(calls.map((c) => c.url)).toEqual(['/api/v1/admin/orders']);
  });

  it('does not try to refresh when login itself fails with 401', async () => {
    const calls = fakeServer(() => ({ status: 401, body: { success: false, message: 'Username หรือ Password ไม่ถูกต้อง' } }));

    await expect(api.post('/api/v1/auth/login', {})).rejects.toBeTruthy();

    expect(calls.map((c) => c.url)).toEqual(['/api/v1/auth/login']);
  });

  it('sends the user to /login when refresh fails on a protected page', async () => {
    fakeServer(() => ({ status: 401, body: { success: false } }));

    await expect(api.get('/api/orders')).rejects.toBeTruthy();

    expect(window.location.href).toBe('/login');
  });

  it('stays put when refresh fails on a public page like /register', async () => {
    window.location = { pathname: '/register', href: 'http://localhost:3000/register' };
    fakeServer(() => ({ status: 401, body: { success: false } }));

    await expect(api.get('/api/v1/auth/me')).rejects.toBeTruthy();

    expect(window.location.href).toBe('http://localhost:3000/register');
  });
});
