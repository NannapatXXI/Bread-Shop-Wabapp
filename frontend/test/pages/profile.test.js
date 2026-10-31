import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

// user ปัจจุบัน — updateUser แก้ค่าใน object นี้เหมือน AuthContext จริง
const auth = { user: null, loading: false, logout: vi.fn(), updateUser: vi.fn() };
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => auth }));

// แผนที่จริงใช้ Leaflet (ต้องมี window/canvas จริง) — แทนด้วยปุ่มที่ "ยืนยันตำแหน่ง" ให้เลย
vi.mock('next/dynamic', () => ({
  default: () => function FakeMapPicker({ onConfirm }) {
    return (
      <button type="button" onClick={() => onConfirm({
        address: '1 ถ.สีลม', subdistrict: 'สีลม', district: 'บางรัก', province: 'กรุงเทพมหานคร', postcode: '10500',
      })}>ยืนยันตำแหน่งบนแผนที่</button>
    );
  },
}));

const { default: ProfilePage } = await import('@/app/(user)/profile/page');

const HOME = {
  id: 1, name: 'บ้าน', recipientName: 'Alice', phone: '0811111111', address: '99/1 ถ.สุขุมวิท',
  subdistrict: 'คลองตัน', district: 'คลองเตย', province: 'กรุงเทพมหานคร', postcode: '10110', isDefault: true,
};
const OFFICE = { ...HOME, id: 2, name: 'ที่ทำงาน', isDefault: false };

/** backend ปลอม: ที่อยู่ = list ที่ส่งมา; อย่างอื่นตอบ ok — override ได้ต่อ request */
function backend(addresses = [HOME, OFFICE], overrides = () => null) {
  return fakeApi((config) => overrides(config)
    ?? (config.url === '/api/v1/users/me/addresses' && config.method === 'get' ? ok(addresses)
      : config.url === '/api/v1/auth/otp' ? ok({ token: 'otp-token' })
      : config.url === '/api/v1/auth/otp/verification' ? ok({ token: 'reset-token' })
      : ok(null)));
}

const section = (heading) => screen.getByRole('heading', { name: heading }).closest('section');

beforeEach(() => {
  vi.clearAllMocks();
  auth.user = { id: 7, username: 'alice', email: 'alice@example.com', role: 'USER' };
  auth.updateUser = vi.fn((patch) => Object.assign(auth.user, patch));
});

// ───────────── ข้อมูลส่วนตัว ─────────────

describe('Profile — personal info', () => {
  it('shows the name and email', async () => {
    backend();
    render(<ProfilePage />);

    expect(screen.getByRole('heading', { level: 1, name: 'alice' })).toBeInTheDocument();
    expect(within(section('ข้อมูลส่วนตัว')).getByText('alice@example.com')).toBeInTheDocument();
  });

  it('edit → save sends PATCH /users/me and updates the logged-in user everywhere', async () => {
    const calls = backend();
    render(<ProfilePage />);
    const info = section('ข้อมูลส่วนตัว');

    await userEvent.click(within(info).getByRole('button', { name: /แก้ไข/ }));
    const input = within(info).getByLabelText('ชื่อผู้ใช้');
    await userEvent.clear(input);
    await userEvent.type(input, 'alice_new');
    await userEvent.click(within(info).getByRole('button', { name: 'บันทึก' }));

    expect(calls.find(c => c.method === 'patch')).toEqual({ method: 'patch', url: '/api/v1/users/me', body: { username: 'alice_new' } });
    expect(auth.updateUser).toHaveBeenCalledWith({ username: 'alice_new' });
    expect(within(info).getByText('บันทึกสำเร็จ')).toHaveClass('alert-ok');
  });

  it('after saving, a later "ยกเลิก" keeps the NEW name (not the one from login)', async () => {
    backend();
    render(<ProfilePage />);
    const info = section('ข้อมูลส่วนตัว');

    await userEvent.click(within(info).getByRole('button', { name: /แก้ไข/ }));
    await userEvent.clear(within(info).getByLabelText('ชื่อผู้ใช้'));
    await userEvent.type(within(info).getByLabelText('ชื่อผู้ใช้'), 'alice_new');
    await userEvent.click(within(info).getByRole('button', { name: 'บันทึก' }));

    await userEvent.click(within(info).getByRole('button', { name: /แก้ไข/ }));
    await userEvent.type(within(info).getByLabelText('ชื่อผู้ใช้'), 'xxx');
    await userEvent.click(within(info).getByRole('button', { name: 'ยกเลิก' }));

    expect(screen.getByRole('heading', { level: 1, name: 'alice_new' })).toBeInTheDocument();
  });

  it('a backend rejection shows ITS reason as an error (red), not as success', async () => {
    backend(undefined, (config) => (config.method === 'patch' ? fail(400, 'username ห้ามว่าง') : null));
    render(<ProfilePage />);
    const info = section('ข้อมูลส่วนตัว');

    await userEvent.click(within(info).getByRole('button', { name: /แก้ไข/ }));
    await userEvent.clear(within(info).getByLabelText('ชื่อผู้ใช้'));
    await userEvent.type(within(info).getByLabelText('ชื่อผู้ใช้'), '   ');
    await userEvent.click(within(info).getByRole('button', { name: 'บันทึก' }));

    expect(await within(info).findByText('username ห้ามว่าง')).toHaveClass('alert-error');
    expect(auth.updateUser).not.toHaveBeenCalled();
  });
});

// ───────────── เปลี่ยนรหัสผ่าน ─────────────

describe('Profile — change password (OTP)', () => {
  async function goToNewPassword(calls) {
    const security = section('ความปลอดภัย');
    await userEvent.click(within(security).getByRole('button', { name: /เปลี่ยนรหัสผ่าน/ }));
    await userEvent.type(await within(security).findByLabelText('รหัส OTP'), '123456');
    await userEvent.click(within(security).getByRole('button', { name: 'ยืนยัน' }));
    await within(security).findByLabelText('รหัสผ่านใหม่');
    return { security, calls };
  }

  it('full flow: send OTP to own email → verify → set new password', async () => {
    const calls = backend();
    render(<ProfilePage />);

    const { security } = await goToNewPassword(calls);
    expect(calls.find(c => c.url === '/api/v1/auth/otp').body).toEqual({ email: 'alice@example.com' });
    expect(calls.find(c => c.url === '/api/v1/auth/otp/verification').body).toEqual({ token: 'otp-token', otp: '123456' });

    await userEvent.type(within(security).getByLabelText('รหัสผ่านใหม่'), 'brandNew99');
    await userEvent.type(within(security).getByLabelText('ยืนยันรหัสผ่านใหม่'), 'brandNew99');
    await userEvent.click(within(security).getByRole('button', { name: 'บันทึกรหัสผ่านใหม่' }));

    expect(calls.find(c => c.url === '/api/v1/auth/password')).toEqual({
      method: 'put', url: '/api/v1/auth/password', body: { token: 'otp-token', newPassword: 'brandNew99' } });
    expect(await within(security).findByText('เปลี่ยนรหัสผ่านสำเร็จ')).toHaveClass('alert-ok');
  });

  it('OTP must be 6 digits; letters are dropped', async () => {
    backend();
    render(<ProfilePage />);
    const security = section('ความปลอดภัย');

    await userEvent.click(within(security).getByRole('button', { name: /เปลี่ยนรหัสผ่าน/ }));
    const otp = await within(security).findByLabelText('รหัส OTP');
    await userEvent.type(otp, '12ab3');

    expect(otp).toHaveValue('123');
    expect(within(security).getByRole('button', { name: 'ยืนยัน' })).toBeDisabled();
  });

  it('wrong OTP → error, stays on the OTP step', async () => {
    backend(undefined, (config) => (config.url === '/api/v1/auth/otp/verification' ? fail(400, 'OTP ไม่ถูกต้อง') : null));
    render(<ProfilePage />);
    const security = section('ความปลอดภัย');

    await userEvent.click(within(security).getByRole('button', { name: /เปลี่ยนรหัสผ่าน/ }));
    await userEvent.type(await within(security).findByLabelText('รหัส OTP'), '999999');
    await userEvent.click(within(security).getByRole('button', { name: 'ยืนยัน' }));

    expect(await within(security).findByText('OTP ไม่ถูกต้องหรือหมดอายุ')).toHaveClass('alert-error');
    expect(within(security).getByLabelText('รหัส OTP')).toBeInTheDocument();
  });

  it('new password: too short or not matching is caught before calling the backend', async () => {
    const calls = backend();
    render(<ProfilePage />);
    const { security } = await goToNewPassword(calls);

    await userEvent.type(within(security).getByLabelText('รหัสผ่านใหม่'), 'short');
    await userEvent.type(within(security).getByLabelText('ยืนยันรหัสผ่านใหม่'), 'short');
    await userEvent.click(within(security).getByRole('button', { name: 'บันทึกรหัสผ่านใหม่' }));
    expect(within(security).getByText('รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร')).toBeInTheDocument();

    await userEvent.clear(within(security).getByLabelText('รหัสผ่านใหม่'));
    await userEvent.type(within(security).getByLabelText('รหัสผ่านใหม่'), 'longEnough1');
    await userEvent.clear(within(security).getByLabelText('ยืนยันรหัสผ่านใหม่'));
    await userEvent.type(within(security).getByLabelText('ยืนยันรหัสผ่านใหม่'), 'different1');
    await userEvent.click(within(security).getByRole('button', { name: 'บันทึกรหัสผ่านใหม่' }));
    expect(within(security).getAllByText('รหัสผ่านไม่ตรงกัน').length).toBeGreaterThan(0);

    expect(calls.some(c => c.url === '/api/v1/auth/password')).toBe(false);
  });

  it('"ยกเลิก" goes back to the start', async () => {
    const calls = backend();
    render(<ProfilePage />);
    const { security } = await goToNewPassword(calls);

    await userEvent.click(within(security).getByRole('button', { name: 'ยกเลิก' }));

    expect(within(security).queryByLabelText('รหัสผ่านใหม่')).not.toBeInTheDocument();
    expect(within(security).getByRole('button', { name: /เปลี่ยนรหัสผ่าน/ })).toBeInTheDocument();
  });
});

// ───────────── ที่อยู่ ─────────────

describe('Profile — addresses', () => {
  it('lists addresses, the default one is marked', async () => {
    backend();
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');

    expect(await within(box).findByText('บ้าน')).toBeInTheDocument();
    expect(within(box).getByText('ที่อยู่หลัก')).toBeInTheDocument();
    expect(within(box).getAllByText('99/1 ถ.สุขุมวิท คลองตัน คลองเตย กรุงเทพมหานคร 10110', { exact: false })).toHaveLength(2);
    // ตัวที่เป็นที่อยู่หลักอยู่แล้วไม่มีปุ่ม "ตั้งเป็นที่อยู่หลัก" — มีแค่ตัวอื่น
    expect(within(box).getAllByRole('button', { name: 'ตั้งเป็นที่อยู่หลัก' })).toHaveLength(1);
  });

  it('no address yet → empty state', async () => {
    backend([]);
    render(<ProfilePage />);

    expect(await screen.findByText('ยังไม่มีที่อยู่')).toBeInTheDocument();
  });

  it('add: save needs recipient + phone, sends the form, then reloads', async () => {
    const calls = backend([]);
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');

    await userEvent.click(within(box).getByRole('button', { name: /เพิ่มที่อยู่/ }));
    const save = within(box).getByRole('button', { name: 'บันทึก' });
    expect(save).toBeDisabled();

    await userEvent.type(within(box).getByLabelText(/ชื่อที่อยู่/), 'คอนโด');
    await userEvent.type(within(box).getByLabelText(/ชื่อผู้รับ/), 'Alice');
    await userEvent.type(within(box).getByLabelText(/เบอร์โทรศัพท์/), '0822222222');
    await userEvent.click(within(box).getByLabelText('ตั้งเป็นที่อยู่หลัก'));
    await userEvent.click(save);

    const post = calls.find(c => c.method === 'post');
    expect(post.url).toBe('/api/v1/users/me/addresses');
    expect(post.body).toMatchObject({ name: 'คอนโด', recipientName: 'Alice', phone: '0822222222', isDefault: true });
    expect(calls.filter(c => c.method === 'get' && c.url === '/api/v1/users/me/addresses')).toHaveLength(2);
  });

  it('picking on the map fills the address fields but keeps name and phone', async () => {
    const calls = backend([]);
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');

    await userEvent.click(within(box).getByRole('button', { name: /เพิ่มที่อยู่/ }));
    await userEvent.type(within(box).getByLabelText(/ชื่อผู้รับ/), 'Alice');
    await userEvent.type(within(box).getByLabelText(/เบอร์โทรศัพท์/), '0822222222');
    await userEvent.click(within(box).getByRole('button', { name: /เลือกจากแผนที่/ }));
    await userEvent.click(screen.getByRole('button', { name: 'ยืนยันตำแหน่งบนแผนที่' }));

    expect(within(box).getByLabelText('จังหวัด')).toHaveValue('กรุงเทพมหานคร');
    expect(within(box).getByLabelText('รหัสไปรษณีย์')).toHaveValue('10500');
    expect(within(box).getByLabelText(/ชื่อผู้รับ/)).toHaveValue('Alice');

    await userEvent.click(within(box).getByRole('button', { name: 'บันทึก' }));
    expect(calls.find(c => c.method === 'post').body).toMatchObject({ address: '1 ถ.สีลม', recipientName: 'Alice', phone: '0822222222' });
  });

  it('edit: the form is pre-filled and saves with PUT to that address', async () => {
    const calls = backend();
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');
    await within(box).findByText('ที่ทำงาน');

    const officeCard = within(box).getByText('ที่ทำงาน').closest('.opt');
    await userEvent.click(within(officeCard).getByRole('button', { name: /แก้ไข/ }));
    expect(within(box).getByRole('heading', { name: 'แก้ไขที่อยู่' })).toBeInTheDocument();
    expect(within(box).getByLabelText(/ชื่อผู้รับ/)).toHaveValue('Alice');

    await userEvent.clear(within(box).getByLabelText(/เบอร์โทรศัพท์/));
    await userEvent.type(within(box).getByLabelText(/เบอร์โทรศัพท์/), '0899999999');
    await userEvent.click(within(box).getByRole('button', { name: 'บันทึก' }));

    const put = calls.find(c => c.method === 'put');
    expect(put.url).toBe('/api/v1/users/me/addresses/2');
    expect(put.body.phone).toBe('0899999999');
  });

  it('delete asks first; "cancel" does nothing, "OK" deletes', async () => {
    const calls = backend();
    const confirm = vi.spyOn(window, 'confirm');
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');
    await within(box).findByText('ที่ทำงาน');
    const officeCard = within(box).getByText('ที่ทำงาน').closest('.opt');

    confirm.mockReturnValueOnce(false);
    await userEvent.click(within(officeCard).getByRole('button', { name: /ลบ/ }));
    expect(calls.some(c => c.method === 'delete')).toBe(false);

    confirm.mockReturnValueOnce(true);
    await userEvent.click(within(officeCard).getByRole('button', { name: /ลบ/ }));
    expect(calls.find(c => c.method === 'delete').url).toBe('/api/v1/users/me/addresses/2');
  });

  it('"ตั้งเป็นที่อยู่หลัก" calls the default endpoint for that address', async () => {
    const calls = backend();
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');

    await userEvent.click(await within(box).findByRole('button', { name: 'ตั้งเป็นที่อยู่หลัก' }));

    expect(calls.find(c => c.method === 'put').url).toBe('/api/v1/users/me/addresses/2/default');
  });

  it('a failed save is shown as an error', async () => {
    backend([], (config) => (config.method === 'post' ? fail(400, 'ข้อมูลไม่ถูกต้อง') : null));
    render(<ProfilePage />);
    const box = section('ที่อยู่จัดส่ง');

    await userEvent.click(within(box).getByRole('button', { name: /เพิ่มที่อยู่/ }));
    await userEvent.type(within(box).getByLabelText(/ชื่อผู้รับ/), 'Alice');
    await userEvent.type(within(box).getByLabelText(/เบอร์โทรศัพท์/), '0822222222');
    await userEvent.click(within(box).getByRole('button', { name: 'บันทึก' }));

    expect(await within(box).findByText('บันทึกที่อยู่ไม่สำเร็จ')).toHaveClass('alert-error');
  });
});

it('the logout button logs out', async () => {
  backend();
  render(<ProfilePage />);

  await userEvent.click(screen.getByRole('button', { name: /ออกจากระบบ/ }));

  expect(auth.logout).toHaveBeenCalled();
});
