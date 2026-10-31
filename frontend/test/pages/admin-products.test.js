import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

const router = { push: vi.fn(), replace: vi.fn(), back: vi.fn() };
vi.mock('next/navigation', () => ({ useRouter: () => router }));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));
vi.mock('react-easy-crop', () => ({ default: () => null }));

const { default: AdminProductsPage } = await import('@/app/admin/products/page');

const product = (id, name, { stock = 20, isActive = true } = {}) =>
  ({ id, name, stock, isActive, price: 20, description: '', imageUrl: null, category: 'BREAD', expiryDate: null });
const PRODUCTS = [product(1, 'donut'), product(2, 'Bread'), product(3, 'Old Cookie', { isActive: false })];

function backend(overrides = () => null) {
  return fakeApi((config) => overrides(config) ?? (config.url === '/api/v1/admin/products' ? ok(PRODUCTS) : ok(null)));
}

const row = (name) => screen.getByText(name).closest('tr');

beforeEach(() => vi.clearAllMocks());

describe('Admin products — close sale instead of delete', () => {
  it('a closed product is labelled "ปิดการขาย" and its switch is off', async () => {
    backend();
    render(<AdminProductsPage />);

    const closed = row(await screen.findByText('Old Cookie').then(() => 'Old Cookie'));
    expect(within(closed).getByText('ปิดการขาย')).toBeInTheDocument();
    expect(within(closed).getByRole('button', { name: 'เปิดขาย Old Cookie' })).toHaveAttribute('aria-pressed', 'false');
    expect(within(row('donut')).getByRole('button', { name: 'เปิดขาย donut' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('turning the switch off closes the sale (PATCH active=false) and updates the row', async () => {
    const calls = backend();
    render(<AdminProductsPage />);
    await screen.findByText('donut');

    await userEvent.click(screen.getByRole('button', { name: 'เปิดขาย donut' }));

    expect(calls.at(-1)).toEqual({ method: 'patch', url: '/api/v1/admin/products/1/active', body: { active: false } });
    expect(within(row('donut')).getByText('ปิดการขาย')).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith('ปิดการขาย donut แล้ว');
  });

  it('turning it back on reopens the sale', async () => {
    const calls = backend();
    render(<AdminProductsPage />);
    await screen.findByText('Old Cookie');

    await userEvent.click(screen.getByRole('button', { name: 'เปิดขาย Old Cookie' }));

    expect(calls.at(-1).body).toEqual({ active: true });
    expect(within(row('Old Cookie')).queryByText('ปิดการขาย')).not.toBeInTheDocument();
  });

  it('the "ปิดการขาย" stat filters the table to closed products only', async () => {
    backend();
    render(<AdminProductsPage />);
    await screen.findByText('donut');

    await userEvent.click(screen.getByRole('button', { name: /ปิดการขาย\s*●\s*1/ }));

    expect(screen.getByText('Old Cookie')).toBeInTheDocument();
    expect(screen.queryByText('donut')).not.toBeInTheDocument();
  });

  it('the delete dialog offers "ปิดการขายแทน", which closes the sale and does not delete', async () => {
    const calls = backend();
    render(<AdminProductsPage />);
    await screen.findByText('Bread');

    await userEvent.click(screen.getByRole('button', { name: 'ลบ Bread' }));
    const dialog = screen.getByRole('alertdialog', { name: 'ลบสินค้า' });
    expect(within(dialog).getByText(/จะกลับมาขายเมนูนี้อีก/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'ปิดการขายแทน' }));

    expect(calls.some(c => c.method === 'delete')).toBe(false);
    expect(calls.at(-1)).toMatchObject({ method: 'patch', url: '/api/v1/admin/products/2/active', body: { active: false } });
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('deleting a product that was already ordered shows the backend reason (409)', async () => {
    backend((config) => (config.method === 'delete'
      ? fail(409, 'สินค้านี้มีในออเดอร์แล้ว ลบไม่ได้ — ใช้ "ปิดการขาย" แทน') : null));
    render(<AdminProductsPage />);
    await screen.findByText('Bread');

    await userEvent.click(screen.getByRole('button', { name: 'ลบ Bread' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'ลบ' }));

    expect(toast.error).toHaveBeenCalledWith('สินค้านี้มีในออเดอร์แล้ว ลบไม่ได้ — ใช้ "ปิดการขาย" แทน');
    expect(screen.getByText('Bread')).toBeInTheDocument();
  });
});
