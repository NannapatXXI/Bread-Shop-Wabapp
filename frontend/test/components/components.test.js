import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProductCard from '@/app/components/ProductCard';
import StatusBadge from '@/app/components/StatusBadge';
import PasswordField, { PasswordRules } from '@/app/components/PasswordField';

const product = (over = {}) => ({ id: 1, name: 'Donut', price: 20, stock: 10, description: 'เคลือบน้ำตาล', imageUrl: 'u/1.jpg', ...over });

describe('ProductCard', () => {
  it('shows name, description, formatted price and calls onAdd with the product', async () => {
    const onAdd = vi.fn();
    render(<ProductCard product={product({ price: 1250 })} imageSrc="http://img/1.jpg" inCart={0} onAdd={onAdd} />);

    expect(screen.getByRole('heading', { name: 'Donut' })).toBeInTheDocument();
    expect(screen.getByText('เคลือบน้ำตาล')).toBeInTheDocument();
    expect(screen.getByText(/฿1,250/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Donut' })).toHaveAttribute('src', 'http://img/1.jpg');

    await userEvent.click(screen.getByRole('button', { name: 'เพิ่ม Donut ลงตะกร้า' }));
    expect(onAdd).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
  });

  it('sold out: tag shown, button disabled, onAdd never called', async () => {
    const onAdd = vi.fn();
    render(<ProductCard product={product({ stock: 0 })} inCart={0} onAdd={onAdd} />);

    expect(screen.getByText('หมดแล้ว')).toBeInTheDocument();
    const button = screen.getByRole('button', { name: 'Donut หมดสต็อก' });
    expect(button).toBeDisabled();
    await userEvent.click(button);
    expect(onAdd).not.toHaveBeenCalled();
  });

  it('cart already holds all the stock: disabled with "ครบแล้ว"', () => {
    render(<ProductCard product={product({ stock: 3 })} inCart={3} onAdd={vi.fn()} />);

    expect(screen.getByText('ครบแล้ว')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Donut ครบจำนวนในสต็อกแล้ว' })).toBeDisabled();
  });

  it('stock label: "มี" when plenty, "เหลือ" when 5 or fewer, hidden when not requested', () => {
    const { rerender } = render(<ProductCard product={product({ stock: 12 })} inCart={0} onAdd={vi.fn()} showStock />);
    expect(screen.getByText('มี 12 ชิ้น')).toBeInTheDocument();

    rerender(<ProductCard product={product({ stock: 4 })} inCart={0} onAdd={vi.fn()} showStock />);
    expect(screen.getByText('เหลือ 4 ชิ้น')).toHaveClass('low');

    rerender(<ProductCard product={product({ stock: 4 })} inCart={0} onAdd={vi.fn()} />);
    expect(screen.queryByText(/ชิ้น/)).not.toBeInTheDocument();
  });

  it('no image: shows placeholder text instead of a broken <img>', () => {
    render(<ProductCard product={product({ imageUrl: null })} inCart={0} onAdd={vi.fn()} />);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('ไม่มีรูปสินค้า')).toBeInTheDocument();
  });
});

describe('StatusBadge', () => {
  it.each([
    ['PENDING', 'b-amber'],
    ['CONFIRMED', 'b-blue'],
    ['SHIPPED', 'b-violet'],
    ['DELIVERED', 'b-green'],
    ['CANCELLED', 'b-red'],
  ])('%s uses %s', (status, cls) => {
    render(<StatusBadge status={status} label="x" />);
    expect(screen.getByText('x')).toHaveClass('badge', cls);
  });

  it('falls back to the raw status when no label is given', () => {
    render(<StatusBadge status="WEIRD" />);
    expect(screen.getByText('WEIRD')).toHaveClass('badge');
  });
});

describe('PasswordField / PasswordRules', () => {
  it('toggles visibility and keeps the value', async () => {
    const onChange = vi.fn();
    render(<PasswordField id="pw" label="รหัสผ่าน" value="secret1" onChange={onChange} />);
    const input = screen.getByLabelText('รหัสผ่าน');

    expect(input).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'แสดงรหัสผ่าน' }));
    expect(input).toHaveAttribute('type', 'text');
    expect(input).toHaveValue('secret1');
    await userEvent.click(screen.getByRole('button', { name: 'ซ่อนรหัสผ่าน' }));
    expect(input).toHaveAttribute('type', 'password');
  });

  it('marks each rule as met only when it really is', () => {
    const { rerender } = render(<PasswordRules password="abc" />);
    const [length, mix] = screen.getAllByText(/•|✓/);
    expect(length).not.toHaveClass('ok');
    expect(mix).not.toHaveClass('ok');

    rerender(<PasswordRules password="abcdef1" />);
    expect(screen.getByText(/ตัวเลขหรือสัญลักษณ์/)).toHaveClass('ok');
  });

  it('length rule matches what the backend accepts (RegisterRequest: at least 8 characters)', () => {
    render(<PasswordRules password="abcde1x" />);

    expect(screen.getByText(/อย่างน้อย \d ตัวอักษร/)).not.toHaveClass('ok');
  });
});
