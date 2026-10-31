import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const { default: AdminCustomersPage } = await import('@/app/admin/customers/page');

const customer = (id, username, extra = {}) => ({
  id, username, email: `${username}@example.com`, role: 'USER', provider: 'credentials',
  createdAt: '2026-10-01T10:00:00', orderCount: 0, isActive: true, ...extra,
});
const CUSTOMERS = [
  customer(7, 'alice', { orderCount: 2 }),
  customer(8, 'bob', { provider: 'google' }),
  customer(1, 'admin', { role: 'ADMIN' }),
  customer(9, 'carol', { isActive: false }),
];
const ALICE_ORDERS = [
  { id: 101, orderNo: 'ORD-ALICE-1', totalAmount: 120, status: 'DELIVERED', orderLines: [{ productName: 'donut' }, { productName: 'Bread' }] },
  { id: 102, orderNo: 'ORD-ALICE-2', totalAmount: 40, status: 'REFUNDED', orderLines: [{ productName: 'donut' }] },
];

function backend(overrides = () => null) {
  return fakeApi((config) => overrides(config)
    ?? (config.url === '/api/v1/admin/customers' ? ok(CUSTOMERS)
      : config.url === '/api/v1/admin/customers/7/orders' ? ok(ALICE_ORDERS)
      : config.url === '/api/v1/orders' ? ok([{ id: 999, orderNo: 'ORD-OF-THE-ADMIN', totalAmount: 1, status: 'PENDING', orderLines: [] }])
      : ok(null)));
}

const rowOf = (username) => within(screen.getByRole('table')).getByText(username, { selector: 'b' }).closest('tr');
async function manage(username) {
  await userEvent.click(within(rowOf(username)).getByRole('button', { name: 'จัดการ' }));
  return screen.getByRole('dialog', { name: 'จัดการผู้ใช้' });
}

beforeEach(() => vi.clearAllMocks());

describe('Admin customers — list', () => {
  it('shows counts by sign-in method, role and a banned badge', async () => {
    backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const chip = (name) => screen.getByRole('button', { name: new RegExp(`^${name}`) });
    expect(chip('ทั้งหมด')).toHaveTextContent('4');
    expect(chip('Google')).toHaveTextContent('1');
    expect(chip('Email')).toHaveTextContent('3');
    expect(chip('แอดมิน')).toHaveTextContent('1');
    expect(within(rowOf('carol')).getByText('ถูกระงับ')).toBeInTheDocument();
  });

  it('filters by tab and searches name or email', async () => {
    backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    await userEvent.click(screen.getByRole('button', { name: /^Google/ }));
    expect(screen.getByText('bob', { selector: 'b' })).toBeInTheDocument();
    expect(screen.queryByText('alice', { selector: 'b' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /^ทั้งหมด/ }));
    await userEvent.type(screen.getByRole('textbox', { name: 'ค้นหาลูกค้า' }), 'carol@');
    expect(screen.getByText('carol', { selector: 'b' })).toBeInTheDocument();
    expect(screen.queryByText('bob', { selector: 'b' })).not.toBeInTheDocument();
  });
});

describe('Admin customers — manage', () => {
  it('save sends only what changed: role and trimmed username', async () => {
    const calls = backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const dialog = await manage('alice');
    await userEvent.click(within(dialog).getByRole('button', { name: 'แอดมิน' }));
    const name = within(dialog).getByLabelText('ชื่อผู้ใช้');
    await userEvent.clear(name);
    await userEvent.type(name, '  alice2  ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    const patches = calls.filter(c => c.method === 'patch');
    expect(patches).toEqual(expect.arrayContaining([
      { method: 'patch', url: '/api/v1/admin/customers/7/role', body: { role: 'ADMIN' } },
      { method: 'patch', url: '/api/v1/admin/customers/7/username', body: { username: 'alice2' } },
    ]));
    expect(toast.success).toHaveBeenCalledWith('อัปเดตสำเร็จ');
    expect(screen.getByText('alice2', { selector: 'b' })).toBeInTheDocument();
  });

  it('nothing changed → no request', async () => {
    const calls = backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const dialog = await manage('alice');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    expect(calls.some(c => c.method === 'patch')).toBe(false);
  });

  it('a backend rejection shows its reason (e.g. blank username)', async () => {
    backend((config) => (config.method === 'patch' ? fail(400, 'username ห้ามว่าง') : null));
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const dialog = await manage('alice');
    await userEvent.clear(within(dialog).getByLabelText('ชื่อผู้ใช้'));
    await userEvent.type(within(dialog).getByLabelText('ชื่อผู้ใช้'), ' ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    expect(toast.error).toHaveBeenCalledWith('username ห้ามว่าง');
    expect(screen.getByRole('dialog', { name: 'จัดการผู้ใช้' })).toBeInTheDocument();
  });

  it('ban → PATCH active=false, the row and dialog show "ถูกระงับ"; unban reverses it', async () => {
    const calls = backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const dialog = await manage('alice');
    await userEvent.click(within(dialog).getByRole('button', { name: 'ระงับ' }));

    expect(calls.at(-1)).toEqual({ method: 'patch', url: '/api/v1/admin/customers/7/active', body: { active: false } });
    expect(within(dialog).getByText('ถูกระงับ')).toBeInTheDocument();
    expect(within(rowOf('alice')).getByText('ถูกระงับ')).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'ปลดระงับ' }));
    expect(calls.at(-1).body).toEqual({ active: true });
  });
});

describe('Admin customers — order history tab', () => {
  it('shows THAT customer\'s orders (admin endpoint), not the logged-in admin\'s own', async () => {
    const calls = backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const dialog = await manage('alice');
    await userEvent.click(within(dialog).getByRole('tab', { name: 'ประวัติออเดอร์' }));

    expect(await within(dialog).findByText('ORD-ALICE-1')).toBeInTheDocument();
    expect(within(dialog).queryByText('ORD-OF-THE-ADMIN')).not.toBeInTheDocument();
    expect(calls.map(c => c.url)).toContain('/api/v1/admin/customers/7/orders');
    expect(within(dialog).getByText('donut, Bread')).toBeInTheDocument();
  });

  it('every order status has a Thai label (incl. คืนเงิน)', async () => {
    backend();
    render(<AdminCustomersPage />);
    await screen.findByText('alice', { selector: 'b' });

    const dialog = await manage('alice');
    await userEvent.click(within(dialog).getByRole('tab', { name: 'ประวัติออเดอร์' }));

    expect(await within(dialog).findByText('คืนเงิน')).toBeInTheDocument();
    expect(within(dialog).queryByText('REFUNDED')).not.toBeInTheDocument();
  });

  it('a customer without orders → "ยังไม่มีออเดอร์"', async () => {
    backend((config) => (config.url === '/api/v1/admin/customers/8/orders' ? ok([]) : null));
    render(<AdminCustomersPage />);
    await screen.findByText('bob', { selector: 'b' });

    const dialog = await manage('bob');
    await userEvent.click(within(dialog).getByRole('tab', { name: 'ประวัติออเดอร์' }));

    expect(await within(dialog).findByText('ยังไม่มีออเดอร์')).toBeInTheDocument();
  });
});
