import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

const router = { push: vi.fn(), replace: vi.fn(), back: vi.fn() };
vi.mock('next/navigation', () => ({ useRouter: () => router, useParams: () => ({ id: '30' }) }));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));
vi.mock('react-easy-crop', () => ({ default: () => null }));

const { default: AddProductPage } = await import('@/app/admin/products/addproduct/page');
const { default: EditProductPage } = await import('@/app/admin/products/editproduct/[id]/page');

// FormData ที่ส่งไป backend → object ธรรมดา ไว้เทียบค่า
const fields = (formData) => Object.fromEntries([...formData.entries()].filter(([k]) => k !== 'image'));

beforeEach(() => vi.clearAllMocks());

describe('Admin: add product', () => {
  async function fillRequired() {
    await userEvent.type(screen.getByLabelText(/ชื่อสินค้า/), 'ครัวซองต์เนย');
    await userEvent.type(screen.getByLabelText(/ราคา/), '45');
    await userEvent.type(screen.getByLabelText(/จำนวนคงเหลือ/), '12');
  }

  it('the main button is labelled and disabled until name, price and stock are filled', async () => {
    fakeApi(() => ok(null));
    render(<AddProductPage />);

    const button = screen.getByRole('button', { name: 'เพิ่มสินค้า' });
    expect(button).toBeDisabled();

    await fillRequired();
    expect(button).toBeEnabled();
  });

  it('opens a confirmation with what will be saved; "แก้ไข" closes it without saving', async () => {
    const calls = fakeApi(() => ok(null));
    render(<AddProductPage />);
    await fillRequired();

    await userEvent.click(screen.getByRole('button', { name: 'เพิ่มสินค้า' }));

    const dialog = screen.getByRole('dialog', { name: 'ยืนยันการเพิ่มสินค้า' });
    expect(within(dialog).getByText('ครัวซองต์เนย')).toBeInTheDocument();
    expect(within(dialog).getByText('฿45')).toBeInTheDocument();
    expect(within(dialog).getByText('12')).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'แก้ไข' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('confirm sends the form to the admin endpoint and goes back to the product list', async () => {
    const calls = fakeApi(() => ok({ id: 30 }));
    render(<AddProductPage />);
    await fillRequired();
    await userEvent.selectOptions(screen.getByLabelText(/หมวดหมู่/), 'CAKE');

    await userEvent.click(screen.getByRole('button', { name: 'เพิ่มสินค้า' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'ยืนยัน' }));

    expect(calls).toHaveLength(1);
    expect(calls[0]).toMatchObject({ method: 'post', url: '/api/v1/admin/products' });
    expect(fields(calls[0].body)).toEqual({
      name: 'ครัวซองต์เนย', price: '45', stock: '12', description: '', category: 'CAKE', expiryDate: '',
    });
    expect(toast.success).toHaveBeenCalledWith('เพิ่มสินค้าสำเร็จ');
    expect(router.push).toHaveBeenCalledWith('/admin/products');
  });

  it('when the backend rejects, shows an error and stays on the page', async () => {
    fakeApi(() => fail(400, 'จำนวนสต็อกต้องไม่ติดลบ'));
    render(<AddProductPage />);
    await fillRequired();

    await userEvent.click(screen.getByRole('button', { name: 'เพิ่มสินค้า' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'ยืนยัน' }));

    expect(toast.error).toHaveBeenCalledWith('เพิ่มสินค้าไม่สำเร็จ');
    expect(router.push).not.toHaveBeenCalled();
  });
});

describe('Admin: edit product', () => {
  const existing = { id: 30, name: 'Croissant', price: 25, stock: 8, description: 'เนยสด', category: 'BREAD', expiryDate: '', imageUrl: 'uploads/c.jpg' };

  it('loads the product, and "บันทึก" → confirm sends the changes with PUT', async () => {
    const calls = fakeApi((config) => (config.method === 'get' ? ok(existing) : ok({ ...existing, name: 'Croissant XL' })));
    render(<EditProductPage />);

    const nameInput = await screen.findByDisplayValue('Croissant');
    expect(calls[0].url).toBe('/api/v1/admin/products/30');
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'Croissant XL');

    await userEvent.click(screen.getByRole('button', { name: 'บันทึก' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('Croissant XL')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'ยืนยัน' }));

    const put = calls.find((c) => c.method === 'put');
    expect(put.url).toBe('/api/v1/admin/products/30');
    expect(fields(put.body)).toEqual({
      name: 'Croissant XL', price: '25', stock: '8', description: 'เนยสด', category: 'BREAD', expiryDate: '',
    });
    expect(put.body.has('image')).toBe(false); // ไม่ได้เลือกรูปใหม่ → ไม่ส่งรูป
    expect(router.push).toHaveBeenCalledWith('/admin/products');
  });

  it('"แก้ไข" in the confirmation closes it without saving', async () => {
    const calls = fakeApi(() => ok(existing));
    render(<EditProductPage />);
    await screen.findByDisplayValue('Croissant');

    await userEvent.click(screen.getByRole('button', { name: 'บันทึก' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'แก้ไข' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'put')).toHaveLength(0);
  });
});
