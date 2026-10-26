import { describe, expect, it } from 'vitest';
import { isAdmin } from '@/app/components/ShopHeader';

// GET /api/v1/users/me ตอบ role เป็น string ("USER" | "ADMIN") — ใช้ตัดสินว่าจะโชว์เมนู admin ไหม
describe('isAdmin', () => {
  it('reads the role string from /api/v1/users/me', () => {
    expect(isAdmin({ id: 1, role: 'ADMIN' })).toBe(true);
    expect(isAdmin({ id: 7, role: 'USER' })).toBe(false);
  });

  it('is false while the user is still loading or logged out', () => {
    expect(isAdmin(null)).toBe(false);
    expect(isAdmin(undefined)).toBe(false);
  });
});
