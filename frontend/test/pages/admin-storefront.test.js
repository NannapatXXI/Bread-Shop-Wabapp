import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const { default: AdminStorefrontPage } = await import('@/app/admin/storefront/page');
const { default: LandingPage } = await import('@/app/page');

const product = (id, name, stock = 5) => ({ id, name, stock, price: 20, imageUrl: null, category: 'BREAD' });
const PRODUCTS = [product(1, 'chiffon', 0), product(2, 'donut'), product(3, 'Bread'), product(4, 'Orange Cake', 0)];

/** backend ปลอม: storefront ตามที่ตั้งไว้ + รายการสินค้า admin; PUT/DELETE ตอบ ok */
function backend({ heroImageUrl = null, featuredProductIds = [] } = {}, overrides = () => null) {
  return fakeApi((config) => {
    const custom = overrides(config);
    if (custom) return custom;
    if (config.url === '/api/v1/admin/storefront' && config.method === 'get') return ok({ heroImageUrl, featuredProductIds });
    if (config.url === '/api/v1/admin/products') return ok(PRODUCTS);
    if (config.url === '/api/v1/admin/storefront/hero-image' && config.method === 'put') return ok({ heroImageUrl: 'uploads/new.jpg' });
    return ok(null);
  });
}

const pickedList = () => screen.queryByRole('list', { name: 'สินค้าแนะนำที่เลือก' });
const pickedNames = () => within(pickedList()).getAllByRole('listitem').map(li => li.textContent);

beforeEach(() => vi.clearAllMocks());

describe('Admin: storefront — featured products', () => {
  it('shows the saved picks in order, and flags a sold-out pick as hidden from customers', async () => {
    backend({ featuredProductIds: [1, 2] });
    render(<AdminStorefrontPage />);

    await screen.findByRole('heading', { name: /สินค้าแนะนำ/ });
    const names = pickedNames();
    expect(names[0]).toContain('chiffon');
    expect(names[0]).toContain('หมด — ลูกค้าไม่เห็น');
    expect(names[1]).toContain('donut');
  });

  it('each slot says whether customers will see it: top in-stock = "ลูกค้าเห็น", past the 4th = "สำรอง"', async () => {
    const shop = [product(1, 'A'), product(2, 'B'), product(3, 'C', 0), product(4, 'D'), product(5, 'E'), product(6, 'F')];
    fakeApi((config) => {
      if (config.url === '/api/v1/admin/storefront') return ok({ heroImageUrl: null, featuredProductIds: [1, 2, 3, 4, 5, 6] });
      if (config.url === '/api/v1/admin/products') return ok(shop);
      return ok(null);
    });
    render(<AdminStorefrontPage />);
    await screen.findByRole('list', { name: 'สินค้าแนะนำที่เลือก' });

    // C หมด → ข้าม; ลูกค้าเห็น A B D E; F เป็นสำรอง
    const slot = (name) => within(pickedList()).getByTitle(name).closest('li');
    expect(slot('A')).toHaveTextContent('ลูกค้าเห็น');
    expect(slot('C')).toHaveTextContent('หมด — ลูกค้าไม่เห็น');
    expect(slot('E')).toHaveTextContent('ลูกค้าเห็น');
    expect(slot('F')).toHaveTextContent('สำรอง');
  });

  it('a closed-for-sale pick is marked as hidden from customers and does not take a visible slot', async () => {
    const shop = [{ ...product(1, 'A'), isActive: false }, product(2, 'B')];
    fakeApi((config) => {
      if (config.url === '/api/v1/admin/storefront') return ok({ heroImageUrl: null, featuredProductIds: [1, 2] });
      if (config.url === '/api/v1/admin/products') return ok(shop);
      return ok(null);
    });
    render(<AdminStorefrontPage />);
    await screen.findByRole('list', { name: 'สินค้าแนะนำที่เลือก' });

    const slot = (name) => within(pickedList()).getByTitle(name).closest('li');
    expect(slot('A')).toHaveTextContent('ปิดการขาย — ลูกค้าไม่เห็น');
    expect(slot('B')).toHaveTextContent('ลูกค้าเห็น');
  });

  it('the product picker shows every product as a card; clicking a picked card removes it', async () => {
    backend({ featuredProductIds: [2] });
    render(<AdminStorefrontPage />);
    const picker = await screen.findByRole('list', { name: 'สินค้าทั้งหมด' });

    const donut = within(picker).getByRole('button', { name: 'เอา donut ออก (อันดับ 1)' });
    expect(donut).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(donut);

    expect(within(picker).getByRole('button', { name: 'เพิ่ม donut' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText(/ยังไม่ได้เลือก/)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('ยังไม่ได้บันทึก');
  });

  it('nothing picked yet → explains that best sellers are shown automatically', async () => {
    backend();
    render(<AdminStorefrontPage />);

    expect(await screen.findByText(/ลูกค้าจะเห็นสินค้าขายดีอัตโนมัติ/)).toBeInTheDocument();
  });

  it('add → reorder → save sends the ids in the new order', async () => {
    const calls = backend({ featuredProductIds: [2] });
    render(<AdminStorefrontPage />);
    await screen.findByRole('list', { name: 'สินค้าแนะนำที่เลือก' });

    const save = screen.getByRole('button', { name: /บันทึก/ });
    expect(save).toBeDisabled();                               // ยังไม่ได้แก้อะไร

    await userEvent.click(screen.getByRole('button', { name: 'เพิ่ม Bread' }));
    await userEvent.click(screen.getByRole('button', { name: 'เลื่อน Bread ขึ้น' }));
    expect(pickedNames()[0]).toContain('Bread');

    await userEvent.click(save);

    const put = calls.find(c => c.method === 'put' && c.url === '/api/v1/admin/storefront/featured');
    expect(put.body).toEqual({ productIds: [3, 2] });
    expect(toast.success).toHaveBeenCalledWith('บันทึกสินค้าแนะนำสำเร็จ');
    expect(save).toBeDisabled();                               // บันทึกแล้ว ไม่มีอะไรค้าง
  });

  it('removing a pick takes it off the list and puts it back into the "add" list', async () => {
    backend({ featuredProductIds: [2, 3] });
    render(<AdminStorefrontPage />);
    await screen.findByRole('list', { name: 'สินค้าแนะนำที่เลือก' });

    await userEvent.click(screen.getByRole('button', { name: 'เอา donut ออก' }));

    expect(pickedNames()).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'เพิ่ม donut' })).toBeInTheDocument();
  });

  it('cannot pick more than 8', async () => {
    const many = Array.from({ length: 9 }, (_, i) => product(10 + i, `ขนม ${i}`));
    fakeApi((config) => {
      if (config.url === '/api/v1/admin/storefront') return ok({ heroImageUrl: null, featuredProductIds: many.slice(0, 8).map(p => p.id) });
      if (config.url === '/api/v1/admin/products') return ok(many);
      return ok(null);
    });
    render(<AdminStorefrontPage />);

    expect(await screen.findByRole('button', { name: 'เพิ่ม ขนม 8' })).toBeDisabled();
  });

  it('a backend error on save is shown and the change stays unsaved', async () => {
    backend({ featuredProductIds: [2] }, (config) =>
      (config.method === 'put' && config.url.endsWith('/featured') ? fail(404, 'Product not found') : null));
    render(<AdminStorefrontPage />);
    await screen.findByRole('list', { name: 'สินค้าแนะนำที่เลือก' });

    await userEvent.click(screen.getByRole('button', { name: 'เพิ่ม Bread' }));
    await userEvent.click(screen.getByRole('button', { name: /บันทึก/ }));

    expect(toast.error).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /บันทึก/ })).toBeEnabled();
  });
});

describe('Admin: storefront — cover image', () => {
  it('uploads the chosen file as multipart "image" and shows the new cover', async () => {
    const calls = backend();
    render(<AdminStorefrontPage />);
    await screen.findByText(/ยังไม่ได้ตั้งรูปปก/);

    const file = new File([new Uint8Array([1, 2])], 'cover.jpg', { type: 'image/jpeg' });
    await userEvent.upload(screen.getByLabelText('เลือกรูปปก'), file);

    const put = calls.find(c => c.method === 'put' && c.url === '/api/v1/admin/storefront/hero-image');
    expect(put.body.get('image')).toBe(file);
    expect(await screen.findByAltText('รูปปกปัจจุบัน')).toHaveAttribute('src', expect.stringContaining('uploads/new.jpg'));
  });

  it('"ใช้รูปตั้งต้น" removes the custom cover', async () => {
    const calls = backend({ heroImageUrl: 'uploads/cover.jpg' });
    render(<AdminStorefrontPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'ใช้รูปตั้งต้น' }));

    expect(calls.some(c => c.method === 'delete' && c.url === '/api/v1/admin/storefront/hero-image')).toBe(true);
    expect(await screen.findByText(/ยังไม่ได้ตั้งรูปปก/)).toBeInTheDocument();
  });
});

describe('Landing page "เมนูขายดี"', () => {
  it('shows the storefront picks from the backend (already in-stock only) — no sold-out badge', async () => {
    const calls = fakeApi(() => ok({ heroImageUrl: null, featured: [product(2, 'donut'), product(3, 'Bread')] }));
    render(<LandingPage />);

    expect(await screen.findByText('donut')).toBeInTheDocument();
    expect(screen.getByText('Bread')).toBeInTheDocument();
    expect(screen.queryByText('หมดแล้ว')).not.toBeInTheDocument();
    expect(calls.map(c => c.url)).toContain('/api/v1/storefront');
  });
});
