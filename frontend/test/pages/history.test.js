import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

const auth = { user: { id: 7, username: 'alice', email: 'alice@example.com' }, loading: false };
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));
// ใบกำกับภาษีมีเทสของตัวเองได้ — ที่นี่เช็คแค่ว่าเปิดด้วย order ที่ถูกต้อง
vi.mock('@/app/components/TaxInvoiceModal', () => ({
  default: ({ order, onClose }) => <div role="dialog" aria-label="ใบกำกับภาษี">{order.orderNo}<button onClick={onClose}>ปิด</button></div>,
}));

const { default: HistoryPage } = await import('@/app/(user)/history/page');

const order = (id, status, extra = {}) => ({
  id, status, orderNo: `ORD-${id}`, createdAt: `2026-10-${10 + id}T10:00:00`,
  shippingName: 'Alice', shippingPhone: '0811111111', shippingAddress: '99/1 ถ.สุขุมวิท',
  shippingSubdistrict: 'คลองตัน', shippingDistrict: 'คลองเตย', shippingProvince: 'กรุงเทพมหานคร', shippingPostcode: '10110',
  subtotal: 100, discountAmount: 0, shippingFee: 0, totalAmount: 100, promotionCode: null, trackingNo: null,
  orderLines: [
    { id: id * 10, productName: 'donut', quantity: 2, totalPrice: 40, productImageUrl: null },
    { id: id * 10 + 1, productName: 'Bread', quantity: 3, totalPrice: 60, productImageUrl: null },
  ],
  ...extra,
});

function backend(orders, overrides = () => null) {
  return fakeApi((config) => overrides(config) ?? (config.url === '/api/v1/orders' ? ok(orders) : ok(null)));
}

const card = (orderNo) => screen.getByText(orderNo).closest('article');
const expand = async (orderNo) => userEvent.click(within(card(orderNo)).getByRole('button', { name: 'ดูรายละเอียด' }));

beforeEach(() => vi.clearAllMocks());

describe('Order history — list', () => {
  it('newest order first, with status, first item, item count and total', async () => {
    backend([order(1, 'DELIVERED'), order(3, 'PENDING'), order(2, 'SHIPPED')]);
    render(<HistoryPage />);

    const cards = await screen.findAllByRole('article');
    expect(cards.map(c => within(c).getByText(/^ORD-/).textContent)).toEqual(['ORD-3', 'ORD-2', 'ORD-1']);
    expect(cards[0]).toHaveTextContent('รอดำเนินการ');
    expect(cards[0]).toHaveTextContent('donut × 2 และอีก 1 รายการ');
    expect(cards[0]).toHaveTextContent('2 รายการ');
    expect(cards[0]).toHaveTextContent('฿100.00');
  });

  it('no orders → empty state with a link to shop', async () => {
    backend([]);
    render(<HistoryPage />);

    expect(await screen.findByText('ยังไม่มีคำสั่งซื้อ')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'เลือกซื้อขนม' })).toHaveAttribute('href', '/product');
  });

  it('a failed load says so (not "ยังไม่มีคำสั่งซื้อ") and can retry', async () => {
    let attempts = 0;
    fakeApi(() => (++attempts === 1 ? fail(500, 'เกิดข้อผิดพลาดในระบบ') : ok([order(1, 'PENDING')])));
    render(<HistoryPage />);

    expect(await screen.findByText('โหลดประวัติการสั่งซื้อไม่สำเร็จ')).toBeInTheDocument();
    expect(screen.queryByText('ยังไม่มีคำสั่งซื้อ')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'ลองอีกครั้ง' }));

    expect(await screen.findByText('ORD-1')).toBeInTheDocument();
  });
});

describe('Order history — details', () => {
  it('"ดูรายละเอียด" shows address, every item and the price breakdown; click again hides it', async () => {
    backend([order(1, 'SHIPPED', { subtotal: 150, discountAmount: 50, promotionCode: 'BREAD50', totalAmount: 100, trackingNo: 'TH999' })]);
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');
    const details = card('ORD-1');
    expect(details).toHaveTextContent('Alice · 0811111111');
    expect(details).toHaveTextContent('99/1 ถ.สุขุมวิท คลองตัน คลองเตย กรุงเทพมหานคร 10110');
    expect(details).toHaveTextContent('Bread × 3');
    expect(details).toHaveTextContent('ส่วนลด (BREAD50)');
    expect(details).toHaveTextContent('-฿50.00');
    expect(details).toHaveTextContent('Tracking: TH999');

    await userEvent.click(within(details).getByRole('button', { name: 'ซ่อนรายละเอียด' }));
    expect(card('ORD-1')).not.toHaveTextContent('Bread × 3');
  });

  it('no discount → no discount row', async () => {
    backend([order(1, 'PENDING')]);
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');

    expect(card('ORD-1')).not.toHaveTextContent('ส่วนลด');
  });

  it('"ออกใบกำกับภาษี" opens the invoice for that order', async () => {
    backend([order(1, 'DELIVERED')]);
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');
    await userEvent.click(screen.getByRole('button', { name: /ออกใบกำกับภาษี/ }));

    expect(screen.getByRole('dialog', { name: 'ใบกำกับภาษี' })).toHaveTextContent('ORD-1');
  });
});

describe('Order history — cancel', () => {
  it('a PENDING order can be cancelled after confirming; the list reloads', async () => {
    const calls = backend([order(1, 'PENDING')]);
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');
    await userEvent.click(within(card('ORD-1')).getByRole('button', { name: /ยกเลิกออเดอร์/ }));
    const confirm = screen.getByRole('alertdialog', { name: 'ยืนยันการยกเลิกออเดอร์' });
    expect(confirm).toHaveTextContent('สินค้าจะถูกคืนสต็อก');

    await userEvent.click(within(confirm).getByRole('button', { name: 'ยกเลิกออเดอร์' }));

    expect(calls.find(c => c.method === 'patch')).toEqual({ method: 'patch', url: '/api/v1/orders/1/cancel', body: undefined });
    expect(toast.success).toHaveBeenCalledWith('ยกเลิกออเดอร์สำเร็จ');
    expect(calls.filter(c => c.url === '/api/v1/orders')).toHaveLength(2);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('"ไม่ยกเลิก" closes the confirmation without cancelling', async () => {
    const calls = backend([order(1, 'PENDING')]);
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');
    await userEvent.click(within(card('ORD-1')).getByRole('button', { name: /ยกเลิกออเดอร์/ }));
    await userEvent.click(screen.getByRole('button', { name: 'ไม่ยกเลิก' }));

    expect(calls.some(c => c.method === 'patch')).toBe(false);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('the backend reason is shown when cancelling fails (e.g. the shop just started preparing)', async () => {
    backend([order(1, 'PENDING')], (config) => (config.method === 'patch'
      ? fail(400, 'ไม่สามารถยกเลิกได้ เนื่องจากออเดอร์อยู่ในสถานะ "PROCESSING" แล้ว') : null));
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');
    await userEvent.click(within(card('ORD-1')).getByRole('button', { name: /ยกเลิกออเดอร์/ }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'ยกเลิกออเดอร์' }));

    expect(toast.error).toHaveBeenCalledWith('ไม่สามารถยกเลิกได้ เนื่องจากออเดอร์อยู่ในสถานะ "PROCESSING" แล้ว');
  });

  it.each([
    ['CONFIRMED',  'ยืนยันแล้ว'],
    ['PROCESSING', 'กำลังเตรียมสินค้า'],
    ['SHIPPED',    'จัดส่งแล้ว'],
    ['DELIVERED',  'ได้รับแล้ว'],
  ])('%s cannot be cancelled and the page explains why', async (status, reason) => {
    backend([order(1, status)]);
    render(<HistoryPage />);
    await screen.findByText('ORD-1');

    await expand('ORD-1');

    expect(within(card('ORD-1')).queryByRole('button', { name: /ยกเลิกออเดอร์/ })).not.toBeInTheDocument();
    expect(card('ORD-1')).toHaveTextContent(`ไม่สามารถยกเลิกได้ — ร้านเปลี่ยนสถานะเป็น “${reason}” แล้ว`);
  });
});
