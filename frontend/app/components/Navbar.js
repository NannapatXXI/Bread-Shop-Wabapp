// components/Navbar.js
'use client';

import { FaBars } from 'react-icons/fa';
import { MdShoppingCart } from 'react-icons/md';
import Link from 'next/link';
import { useCart } from '../CartContext';
import { useAuth } from '../context/AuthContext';
import NotificationBell from './NotificationBell';

export default function Navbar({ setIsOpen }) {
  const { cartCount, toggleCart } = useCart();
  const { user } = useAuth();

  return (
    <nav className="h-16 bg-white shadow-md flex items-center justify-between px-6 z-30 relative">
      {/* ── ซ้าย: Hamburger ─────────────────────────── */}
      <div className="flex items-center">
        <button
          onClick={() => setIsOpen(true)}
          className="text-gray-600 md:hidden mr-4"
        >
          <FaBars size={20} />
        </button>
      </div>

      {/* ── ขวา: username + ตะกร้า ──────────────────── */}
      <div className="flex items-center gap-4">
        {/* ชื่อ user คลิกไปหน้า profile */}
        <Link
          href="/profile"
          className="text-gray-600 hidden md:block hover:text-indigo-500 transition-colors text-sm"
        >
          👋 {user?.username}
        </Link>

        {/* ── กระดิ่งแจ้งเตือน ────────────────────── */}
        <NotificationBell />

        {/* ── ไอคอนตะกร้า + badge ─────────────────── */}
        <button
          onClick={toggleCart}
          className="relative p-1 hover:text-blue-600 transition-colors"
          title="ดูตะกร้าสินค้า"
        >
          <MdShoppingCart size={26} className="text-gray-600 hover:text-[#0B1F33] transition-colors" />

          {/* badge จำนวนสินค้า */}
          {cartCount > 0 && (
            <span className="
              absolute -top-1 -right-1
              bg-red-500 text-white text-xs font-bold
              rounded-full min-w-[18px] h-[18px] px-0.5
              flex items-center justify-center
            ">
              {cartCount > 99 ? '99+' : cartCount}
            </span>
          )}
        </button>
      </div>
    </nav>
  );
}
