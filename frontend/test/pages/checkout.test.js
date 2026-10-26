import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

const router = { push: vi.fn(), replace: vi.fn(), back: vi.fn() };
vi.mock('next/navigation', () => ({ useRouter: () => router, usePathname: () => '/checkout' }));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const cart = { items: [], cartTotal: 0, clearCart: vi.fn() };
vi.mock('@/app/CartContext', () => ({ useCart: () => cart }));
// AuthContext เก็บ user ใน state — object เดิมทุก render (ไม่งั้น useEffect([user]) จะโหลดซ้ำ)
const auth = { user: { id: 7, email: 'alice@example.com' } };
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => auth }));

const { default: CheckoutPage } = await import('@/app/(user)/checkout/page');

const donut = { id: 11, name: 'Donut', price: 20, stock: 10, imageUrl: null };
const cake = { id: 12, name: 'Orange Cake', price: 80, stock: 5, imageUrl: null };
const home = { id: 3, name: 'บ้าน', recipientName: 'Alice', phone: '0811111111', address: '1 Road', subdistrict: 'ลุมพินี', district: 'ปทุมวัน', province: 'กรุงเทพ', postcode: '10330', isDefault: false };
const office = { ...home, id: 4, name: 'ที่ทำงาน', isDefault: true };

function setCart(lines) {
  cart.items = lines;
  cart.cartTotal = lines.reduce((s, l) => s + l.product.price * l.quantity, 0);
}

// ราคาทั้งหน้าอยู่ในสรุปคำสั่งซื้อ — อ่านค่าจากแถว "ยอดรวม"
const summaryTotal = () => within(screen.getByText('ยอดรวม').closest('.r')).getByText(/฿/).textContent;

beforeEach(() => {
  vi.clearAllMocks();
  setCart([{ product: donut, quantity: 2 }, { product: cake, quantity: 1 }]);
});

describe('Checkout page', () => {
  it('sends the user back to products when the cart is empty', async () => {
    setCart([]);
    fakeApi(() => ok([]));

    render(<CheckoutPage />);

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/product'));
  });

  it('loads addresses for the logged-in user and pre-selects the default one', async () => {
    const calls = fakeApi(() => ok([home, office]));

    render(<CheckoutPage />);

    expect(await screen.findByRole('radio', { name: /ที่ทำงาน/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /บ้าน/ })).not.toBeChecked();
    expect(calls[0].url).toBe('/api/v1/users/me/addresses');
  });

  it('without any address the confirm button is disabled and points to the profile page', async () => {
    fakeApi(() => ok([]));

    render(<CheckoutPage />);

    expect(await screen.findByText('ยังไม่มีที่อยู่')).toBeInTheDocument();
    screen.getAllByRole('button', { name: 'ยืนยันคำสั่งซื้อ' }).forEach((b) => expect(b).toBeDisabled());
    expect(screen.getByRole('link', { name: /เพิ่มที่อยู่/ })).toHaveAttribute('href', '/profile');
  });

  it('applies a promo code: sends code + cart total, shows the discount and lowers the total', async () => {
    const calls = fakeApi((config) =>
      config.url.includes('/promotions/discount')
        ? ok({ code: 'BREAD50', name: 'ลด 50', discountAmount: 50 })
        : ok([home]));
    render(<CheckoutPage />);
    await screen.findByRole('radio', { name: /บ้าน/ });

    expect(summaryTotal()).toBe('฿120');
    await userEvent.type(screen.getByLabelText('โค้ดส่วนลด'), 'bread50');
    await userEvent.click(screen.getByRole('button', { name: 'ใช้โค้ด' }));

    expect(await screen.findByText('ส่วนลด (BREAD50)')).toBeInTheDocument();
    expect(summaryTotal()).toBe('฿70');
    const promoCall = calls.find((c) => c.url.includes('/promotions/discount'));
    expect(promoCall.params).toEqual({ code: 'BREAD50', amount: 120 });
  });

  it('never shows a negative total when the discount is larger than the cart', async () => {
    setCart([{ product: donut, quantity: 1 }]);
    fakeApi((config) =>
      config.url.includes('/promotions/discount')
        ? ok({ code: 'BIG', name: 'ลดเยอะ', discountAmount: 500 })
        : ok([home]));
    render(<CheckoutPage />);
    await screen.findByRole('radio', { name: /บ้าน/ });

    await userEvent.type(screen.getByLabelText('โค้ดส่วนลด'), 'BIG');
    await userEvent.click(screen.getByRole('button', { name: 'ใช้โค้ด' }));

    await screen.findByText('ส่วนลด (BIG)');
    expect(summaryTotal()).toBe('฿0');
  });

  it('invalid promo shows the backend reason and keeps the total', async () => {
    fakeApi((config) =>
      config.url.includes('/promotions/discount') ? fail(400, 'โค้ดหมดอายุแล้ว') : ok([home]));
    render(<CheckoutPage />);
    await screen.findByRole('radio', { name: /บ้าน/ });

    await userEvent.type(screen.getByLabelText('โค้ดส่วนลด'), 'OLD');
    await userEvent.click(screen.getByRole('button', { name: 'ใช้โค้ด' }));

    expect(await screen.findByText('โค้ดหมดอายุแล้ว')).toBeInTheDocument();
    expect(summaryTotal()).toBe('฿120');
  });

  it('submits the selected address, promo, trimmed note and every cart line, then clears the cart', async () => {
    const calls = fakeApi((config) => {
      if (config.url.includes('/promotions/discount')) return ok({ code: 'BREAD50', name: 'ลด 50', discountAmount: 50 });
      if (config.url === '/api/v1/orders') return ok({ orderNo: 'ORD-1', totalAmount: 70 }, 'สร้าง order สำเร็จ');
      return ok([home, office]);
    });
    render(<CheckoutPage />);

    await userEvent.click(await screen.findByRole('radio', { name: /บ้าน/ }));
    await userEvent.type(screen.getByLabelText('โค้ดส่วนลด'), 'BREAD50');
    await userEvent.click(screen.getByRole('button', { name: 'ใช้โค้ด' }));
    await screen.findByText('ส่วนลด (BREAD50)');
    await userEvent.type(screen.getByLabelText('หมายเหตุถึงร้าน'), '  โทรก่อนส่ง  ');
    await userEvent.click(screen.getAllByRole('button', { name: 'ยืนยันคำสั่งซื้อ' })[0]);

    expect(await screen.findByText('สั่งซื้อสำเร็จ!')).toBeInTheDocument();
    expect(screen.getByText('ORD-1')).toBeInTheDocument();
    const order = calls.find((c) => c.url === '/api/v1/orders');
    expect(order.body).toEqual({
      userId: 7, addressId: 3, promotionCode: 'BREAD50', note: 'โทรก่อนส่ง',
      items: [{ productId: 11, quantity: 2 }, { productId: 12, quantity: 1 }],
    });
    expect(cart.clearCart).toHaveBeenCalledTimes(1);
  });

  it('when the order fails the cart is kept', async () => {
    fakeApi((config) => (config.url === '/api/v1/orders' ? fail(400, 'สินค้า Donut มีไม่พอ') : ok([home])));
    render(<CheckoutPage />);
    await screen.findByRole('radio', { name: /บ้าน/ });

    await userEvent.click(screen.getAllByRole('button', { name: 'ยืนยันคำสั่งซื้อ' })[0]);

    await waitFor(() => expect(screen.queryByText('สั่งซื้อสำเร็จ!')).not.toBeInTheDocument());
    expect(cart.clearCart).not.toHaveBeenCalled();
  });
});
