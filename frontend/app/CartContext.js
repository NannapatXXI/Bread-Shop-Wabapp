'use client';

// CartContext.js
// เก็บ state ของตะกร้าสินค้าทั้งหมด
// items: [{ product: {...}, quantity: number }]

import { createContext, useContext, useState, useSyncExternalStore } from 'react';
import toast from 'react-hot-toast';

const CartContext = createContext();

// ── cart store: localStorage เป็นแหล่งข้อมูลจริง ─────────────────
// ใช้ useSyncExternalStore แทน useEffect + setState:
//   - ตอน server render / hydration ใช้ EMPTY → ไม่เกิด hydration mismatch
//   - หลัง hydrate React อ่าน localStorage เองแล้ว render ใหม่
//   - เปิดหลายแท็บ ตะกร้าตรงกันผ่าน event 'storage'
const STORAGE_KEY = 'cart';
const EMPTY = [];
const listeners = new Set();
let cachedRaw = null;
let cachedItems = EMPTY;

function readCart() {
  let raw = null;
  try { raw = localStorage.getItem(STORAGE_KEY); } catch { /* storage ถูกปิด */ }
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    try {
      const parsed = JSON.parse(raw || '[]');
      cachedItems = Array.isArray(parsed) ? parsed : EMPTY;
    } catch {
      cachedItems = EMPTY; // ข้อมูลเสีย → เริ่มตะกร้าว่าง ไม่ crash
    }
  }
  return cachedItems;
}

function writeCart(items) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(items)); } catch { /* storage ถูกปิด */ }
  listeners.forEach(l => l());
}

function subscribe(listener) {
  listeners.add(listener);
  window.addEventListener('storage', listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', listener);
  };
}

const getServerSnapshot = () => EMPTY;

export function CartProvider({ children }) {
  const items = useSyncExternalStore(subscribe, readCart, getServerSnapshot);
  const [isOpen, setIsOpen] = useState(false);

  // API เดียวกับ setState เดิม — รับค่าใหม่หรือ function(prev)
  const setItems = (next) => writeCart(typeof next === 'function' ? next(readCart()) : next);

  // ── จำนวนชิ้นรวม (ใช้แสดงบน badge ไอคอน) ──────────────────────
  const cartCount = items.reduce((sum, i) => sum + i.quantity, 0);

  // ── ราคารวม ────────────────────────────────────────────────────
  const cartTotal = items.reduce((sum, i) => sum + i.product.price * i.quantity, 0);

  // ── เพิ่มสินค้า ─────────────────────────────────────────────────
  // ตรวจ stock ก่อนเสมอ — ถ้าตะกร้ามีครบ stock แล้ว → toast error ไม่เพิ่ม
  const addToCart = (product) => {
    const existing = items.find(i => i.product.id === product.id);
    const currentQty = existing ? existing.quantity : 0;

    if (product.stock === 0) {
      toast.error(`"${product.name}" หมดสต็อกแล้ว`);
      return;
    }
    if (currentQty >= product.stock) {
      toast.error(`"${product.name}" มีในสต็อกแค่ ${product.stock} ชิ้น`);
      return;
    }

    setItems(prev => {
      const ex = prev.find(i => i.product.id === product.id);
      if (ex) {
        return prev.map(i =>
          i.product.id === product.id
            ? { ...i, quantity: i.quantity + 1 }
            : i
        );
      }
      return [...prev, { product, quantity: 1 }];
    });
    toast.success(`เพิ่ม "${product.name}" ลงตะกร้าแล้ว!`);
  };

  // ── ลบสินค้าออกจากตะกร้า ────────────────────────────────────────
  const removeFromCart = (productId) => {
    setItems(prev => prev.filter(i => i.product.id !== productId));
  };

  // ── เปลี่ยนจำนวน ────────────────────────────────────────────────
  // qty = 0 → ลบออกเลย / qty > stock → toast error ไม่เปลี่ยน
  const updateQuantity = (productId, qty) => {
    if (qty <= 0) {
      removeFromCart(productId);
      return;
    }
    const item = items.find(i => i.product.id === productId);
    if (item && qty > item.product.stock) {
      toast.error(`มีในสต็อกแค่ ${item.product.stock} ชิ้น`);
      return;
    }
    setItems(prev =>
      prev.map(i =>
        i.product.id === productId ? { ...i, quantity: qty } : i
      )
    );
  };

  // ── ล้างตะกร้าทั้งหมด ──────────────────────────────────────────
  const clearCart = () => {
    setItems([]);
  };

  // ── toggle Drawer ──────────────────────────────────────────────
  const toggleCart  = () => setIsOpen(prev => !prev);
  const openCart    = () => setIsOpen(true);
  const closeCart   = () => setIsOpen(false);

  return (
    <CartContext.Provider value={{
      items,
      cartCount,
      cartTotal,
      isOpen,
      addToCart,
      removeFromCart,
      updateQuantity,
      clearCart,
      toggleCart,
      openCart,
      closeCart,
    }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  return useContext(CartContext);
}
