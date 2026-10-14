import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import toast from 'react-hot-toast';
import { renderToString } from 'react-dom/server';
import { CartProvider, useCart } from '@/app/CartContext';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const donut = { id: 1, name: 'Donut', price: 20, stock: 2 };
const cake = { id: 2, name: 'Orange Cake', price: 80, stock: 5 };
const soldOut = { id: 3, name: 'Chiffon', price: 80, stock: 0 };

const renderCart = () => renderHook(() => useCart(), { wrapper: CartProvider });

describe('CartContext', () => {
  beforeEach(() => vi.clearAllMocks());

  it('adds a product, then increases quantity on the next add', () => {
    const { result } = renderCart();

    act(() => result.current.addToCart(donut));
    act(() => result.current.addToCart(donut));

    expect(result.current.items).toEqual([{ product: donut, quantity: 2 }]);
    expect(result.current.cartCount).toBe(2);
    expect(toast.success).toHaveBeenCalledTimes(2);
  });

  it('never goes above stock and tells the user why', () => {
    const { result } = renderCart();

    act(() => result.current.addToCart(donut));
    act(() => result.current.addToCart(donut));
    act(() => result.current.addToCart(donut));

    expect(result.current.items[0].quantity).toBe(2);
    expect(toast.error).toHaveBeenCalledWith('"Donut" มีในสต็อกแค่ 2 ชิ้น');
  });

  it('refuses sold-out products', () => {
    const { result } = renderCart();

    act(() => result.current.addToCart(soldOut));

    expect(result.current.items).toEqual([]);
    expect(toast.error).toHaveBeenCalledWith('"Chiffon" หมดสต็อกแล้ว');
  });

  it('computes count and total across products', () => {
    const { result } = renderCart();

    act(() => result.current.addToCart(donut));
    act(() => result.current.addToCart(cake));
    act(() => result.current.updateQuantity(cake.id, 3));

    expect(result.current.cartCount).toBe(4);
    expect(result.current.cartTotal).toBe(20 + 80 * 3);
  });

  it('updateQuantity rejects more than stock and keeps the old quantity', () => {
    const { result } = renderCart();
    act(() => result.current.addToCart(cake));

    act(() => result.current.updateQuantity(cake.id, 6));

    expect(result.current.items[0].quantity).toBe(1);
    expect(toast.error).toHaveBeenCalledWith('มีในสต็อกแค่ 5 ชิ้น');
  });

  it('updateQuantity to 0 removes the line', () => {
    const { result } = renderCart();
    act(() => result.current.addToCart(cake));

    act(() => result.current.updateQuantity(cake.id, 0));

    expect(result.current.items).toEqual([]);
  });

  it('remove and clear', () => {
    const { result } = renderCart();
    act(() => result.current.addToCart(donut));
    act(() => result.current.addToCart(cake));

    act(() => result.current.removeFromCart(donut.id));
    expect(result.current.items.map((i) => i.product.id)).toEqual([cake.id]);

    act(() => result.current.clearCart());
    expect(result.current.items).toEqual([]);
  });

  it('persists to localStorage and restores on the next visit', async () => {
    const first = renderCart();
    act(() => first.result.current.addToCart(cake));
    await waitFor(() => expect(JSON.parse(localStorage.getItem('cart'))).toEqual([{ product: cake, quantity: 1 }]));
    first.unmount();

    const second = renderCart();

    await waitFor(() => expect(second.result.current.items).toEqual([{ product: cake, quantity: 1 }]));
  });

  it('ignores a corrupted saved cart instead of crashing', async () => {
    localStorage.setItem('cart', '{not json');

    const { result } = renderCart();

    await waitFor(() => expect(result.current.items).toEqual([]));
  });

  it('server render ignores localStorage, so hydration matches (cart shows after hydrate)', () => {
    localStorage.setItem('cart', JSON.stringify([{ product: cake, quantity: 3 }]));
    function Badge() { return <span>{useCart().cartCount}</span>; }

    const html = renderToString(<CartProvider><Badge /></CartProvider>);

    expect(html).toBe('<span>0</span>');
  });

  it('stays in sync when another tab changes the cart', async () => {
    const { result } = renderCart();

    act(() => {
      localStorage.setItem('cart', JSON.stringify([{ product: donut, quantity: 2 }]));
      window.dispatchEvent(new StorageEvent('storage', { key: 'cart' }));
    });

    await waitFor(() => expect(result.current.cartCount).toBe(2));
  });

  it('drawer open/close/toggle', () => {
    const { result } = renderCart();

    act(() => result.current.openCart());
    expect(result.current.isOpen).toBe(true);
    act(() => result.current.toggleCart());
    expect(result.current.isOpen).toBe(false);
    act(() => result.current.toggleCart());
    act(() => result.current.closeCart());
    expect(result.current.isOpen).toBe(false);
  });
});
