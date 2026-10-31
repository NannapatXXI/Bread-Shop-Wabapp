import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import toast from 'react-hot-toast';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const { default: AdminOrdersPage } = await import('@/app/admin/orders/page');

const order = (id, status, extra = {}) => ({
  id, status,
  orderNo: `ORD-${id}`,
  shippingName: `ลูกค้า ${id}`, shippingPhone: `08000000${String(id).padStart(2, '0')}`,
  shippingAddress: '99/1 ถ.สุขุมวิท', shippingSubdistrict: 'คลองตัน', shippingDistrict: 'คลองเตย',
  shippingProvince: 'กรุงเทพมหานคร', shippingPostcode: '10110',
  totalAmount: 100 + id, trackingNo: null, note: null, createdAt: '2026-10-20T10:30:00',
  orderLines: [{ id: id * 10, productName: 'donut', quantity: 2, totalPrice: 40 }],
  ...extra,
});

function backend(orders, overrides = () => null) {
  return fakeApi((config) => overrides(config) ?? (config.url === '/api/v1/admin/orders' ? ok(orders) : ok(null)));
}

const rows = () => within(screen.getAllByRole('rowgroup')[1]).getAllByRole('row');
const rowOf = (orderNo) => screen.getByText(orderNo).closest('tr');

beforeEach(() => vi.clearAllMocks());

describe('Admin orders — list', () => {
  it('every backend status has a tab with its count (incl. ยืนยันแล้ว and คืนเงิน)', async () => {
    backend([order(1, 'PENDING'), order(2, 'PENDING'), order(3, 'CONFIRMED'), order(4, 'REFUNDED'), order(5, 'SHIPPED')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const tab = (name) => screen.getByRole('tab', { name: new RegExp(name) });
    expect(tab('ทั้งหมด')).toHaveTextContent('5');
    expect(tab('รอดำเนินการ')).toHaveTextContent('2');
    expect(tab('ยืนยันแล้ว')).toHaveTextContent('1');
    expect(tab('คืนเงิน')).toHaveTextContent('1');
    expect(tab('จัดส่งแล้ว')).toHaveTextContent('1');
  });

  it('a refunded / confirmed order shows its real status — never "รอดำเนินการ"', async () => {
    backend([order(3, 'CONFIRMED'), order(4, 'REFUNDED')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-3');

    expect(within(rowOf('ORD-3')).getByText('ยืนยันแล้ว')).toBeInTheDocument();
    expect(within(rowOf('ORD-4')).getByText('คืนเงิน')).toBeInTheDocument();
    expect(screen.queryByText('รอดำเนินการ', { selector: 'td *' })).not.toBeInTheDocument();
  });

  it('a status tab shows only that status', async () => {
    backend([order(1, 'PENDING'), order(2, 'SHIPPED'), order(3, 'DELIVERED')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    await userEvent.click(screen.getByRole('tab', { name: /จัดส่งแล้ว/ }));

    expect(rows()).toHaveLength(1);
    expect(screen.getByText('ORD-2')).toBeInTheDocument();
  });

  it('search matches order number, customer name or phone', async () => {
    backend([order(1, 'PENDING'), order(2, 'PENDING'), order(12, 'PENDING')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');
    const search = screen.getByRole('textbox', { name: 'ค้นหาออเดอร์' });

    await userEvent.type(search, 'ord-12');
    expect(rows()).toHaveLength(1);

    await userEvent.clear(search);
    await userEvent.type(search, 'ลูกค้า 2');
    expect(screen.getByText('ORD-2')).toBeInTheDocument();
    expect(screen.queryByText('ORD-1')).not.toBeInTheDocument();

    await userEvent.clear(search);
    await userEvent.type(search, '0800000001');
    expect(rows()).toHaveLength(1);
  });

  it('no match → empty state', async () => {
    backend([order(1, 'PENDING')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    await userEvent.type(screen.getByRole('textbox', { name: 'ค้นหาออเดอร์' }), 'ไม่มีแน่นอน');

    expect(screen.getByText('ไม่พบออเดอร์')).toBeInTheDocument();
  });

  it('pages 7 orders at a time', async () => {
    backend(Array.from({ length: 9 }, (_, i) => order(i + 1, 'PENDING')));
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    expect(rows()).toHaveLength(7);
    expect(screen.getByText('แสดง 1–7 จาก 9 ออเดอร์')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'ถัดไป' }));

    expect(rows()).toHaveLength(2);
    expect(screen.getByText('ORD-9')).toBeInTheDocument();
  });

  it('a failed load tells the admin', async () => {
    fakeApi(() => fail(500, 'เกิดข้อผิดพลาดในระบบ'));
    render(<AdminOrdersPage />);

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('โหลด orders ไม่สำเร็จ'));
  });
});

describe('Admin orders — update status', () => {
  async function openManage(orderNo) {
    await userEvent.click(within(rowOf(orderNo)).getByRole('button', { name: 'จัดการ' }));
    return screen.getByRole('dialog', { name: 'จัดการออเดอร์' });
  }

  it('the dialog shows the address, note, items and total', async () => {
    backend([order(1, 'PENDING', { note: 'ไม่ใส่น้ำตาล' })]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const dialog = await openManage('ORD-1');

    expect(dialog).toHaveTextContent('ลูกค้า 1 · 0800000001');
    expect(dialog).toHaveTextContent('99/1 ถ.สุขุมวิท ต.คลองตัน อ.คลองเตย กรุงเทพมหานคร 10110');
    expect(dialog).toHaveTextContent('ไม่ใส่น้ำตาล');
    expect(dialog).toHaveTextContent('donut × 2');
    expect(dialog).toHaveTextContent('รวมทั้งสิ้น฿101');
  });

  it('save sends the new status + trimmed tracking number, then reloads', async () => {
    const calls = backend([order(1, 'PENDING')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const dialog = await openManage('ORD-1');
    await userEvent.selectOptions(within(dialog).getByLabelText('สถานะ'), 'SHIPPED');
    await userEvent.type(within(dialog).getByLabelText(/Tracking Number/), '  TH123456789TH  ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    const patch = calls.find(c => c.method === 'patch');
    expect(patch).toEqual({ method: 'patch', url: '/api/v1/admin/orders/1/status', body: { status: 'SHIPPED', trackingNo: 'TH123456789TH' } });
    expect(toast.success).toHaveBeenCalledWith('อัปเดตสถานะสำเร็จ');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(calls.filter(c => c.url === '/api/v1/admin/orders')).toHaveLength(2);   // โหลดใหม่หลังบันทึก
  });

  it('blank tracking number is not sent', async () => {
    const calls = backend([order(1, 'PENDING')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const dialog = await openManage('ORD-1');
    await userEvent.selectOptions(within(dialog).getByLabelText('สถานะ'), 'PROCESSING');
    await userEvent.type(within(dialog).getByLabelText(/Tracking Number/), '   ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    expect(calls.find(c => c.method === 'patch').body).toEqual({ status: 'PROCESSING' });
  });

  it('admin can mark an order as refunded (คืนเงิน)', async () => {
    const calls = backend([order(1, 'DELIVERED')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const dialog = await openManage('ORD-1');
    await userEvent.selectOptions(within(dialog).getByLabelText('สถานะ'), 'คืนเงิน');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    expect(calls.find(c => c.method === 'patch').body).toEqual({ status: 'REFUNDED' });
  });

  it('opening a refunded order pre-selects "คืนเงิน" (not the first option)', async () => {
    backend([order(4, 'REFUNDED')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-4');

    const dialog = await openManage('ORD-4');

    expect(within(dialog).getByLabelText('สถานะ')).toHaveDisplayValue('คืนเงิน');
  });

  it('a backend error is shown and the dialog stays open', async () => {
    backend([order(1, 'PENDING')], (config) => (config.method === 'patch' ? fail(404, 'Order not found') : null));
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const dialog = await openManage('ORD-1');
    await userEvent.click(within(dialog).getByRole('button', { name: 'บันทึก' }));

    expect(toast.error).toHaveBeenCalledWith('Order not found');
    expect(screen.getByRole('dialog', { name: 'จัดการออเดอร์' })).toBeInTheDocument();
  });

  it('"ยกเลิก" closes without saving', async () => {
    const calls = backend([order(1, 'PENDING')]);
    render(<AdminOrdersPage />);
    await screen.findByText('ORD-1');

    const dialog = await openManage('ORD-1');
    await userEvent.click(within(dialog).getByRole('button', { name: 'ยกเลิก' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(calls.some(c => c.method === 'patch')).toBe(false);
  });
});
