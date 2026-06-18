"use client";

import Link from 'next/link';
import Image from 'next/image';
import { usePathname } from 'next/navigation';
import { useAuth } from "../context/AuthContext";
import { FaHome, FaSignOutAlt, FaTimes, FaUsers, FaTags, FaShieldAlt } from 'react-icons/fa';
import { GrHistory } from "react-icons/gr";
import { MdPerson, MdDashboard, MdOutlineManageSearch, MdReceiptLong } from "react-icons/md";
import { VscArchive } from "react-icons/vsc";

export default function Sidebar({ isOpen, setIsOpen }) {
  const { user, logout } = useAuth();
  const pathname = usePathname();

  const linkClass = (href, exact = false) => {
    const isActive = exact ? pathname === href : pathname.startsWith(href);
    return `flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors duration-150 text-sm ${
      isActive
        ? 'bg-blue-600 text-white font-semibold shadow-md'
        : 'text-gray-300 hover:bg-white/10 hover:text-white'
    }`;
  };

  return (
    <>
      {/* Sidebar panel — fixed เสมอ, ซ่อนด้วย translate บน mobile */}
      <aside
        className={`
          fixed inset-y-0 left-0 z-40 w-64
          bg-[#0F2235]
          bg-[radial-gradient(ellipse_at_bottom_left,_rgba(58,123,213,0.2),_transparent)]
          flex flex-col
          transition-transform duration-300 ease-in-out
          ${isOpen ? 'translate-x-0' : '-translate-x-full'}
          md:translate-x-0
        `}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <Image
              src="/logo.png"
              alt="Peak Pung Logo"
              width={44}
              height={44}
              className="rounded-full object-cover flex-shrink-0"
            />
            <div className="leading-tight">
              <p className="text-white font-bold text-sm">Peak Pung</p>
              <p className="text-gray-400 text-xs">by Mom Hmee</p>
            </div>
          </div>
          <button
            onClick={() => setIsOpen(false)}
            className="md:hidden text-gray-400 hover:text-white p-1"
          >
            <FaTimes size={18} />
          </button>
        </div>

        {/* Nav links */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          <Link href="/home" className={linkClass('/home', true)} onClick={() => setIsOpen(false)}>
            <FaHome size={16} />
            Home
          </Link>
          <Link href="/product" className={linkClass('/product')} onClick={() => setIsOpen(false)}>
            <VscArchive size={16} />
            Product
          </Link>
          <Link href="/history" className={linkClass('/history')} onClick={() => setIsOpen(false)}>
            <GrHistory size={16} />
            History
          </Link>
          <Link href="/profile" className={linkClass('/profile')} onClick={() => setIsOpen(false)}>
            <MdPerson size={16} />
            Profile
          </Link>

          {user?.roles?.some(r => r.authority === "ROLE_ADMIN") && (
            <>
              <div className="pt-4 pb-1">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider px-3">
                  Admin Panel
                </p>
              </div>
              <Link href="/admin/dashbord" className={linkClass('/admin/dashbord')} onClick={() => setIsOpen(false)}>
                <MdDashboard size={16} />
                Dashboard
              </Link>
              <Link href="/admin/products" className={linkClass('/admin/products')} onClick={() => setIsOpen(false)}>
                <MdOutlineManageSearch size={16} />
                Products
              </Link>
              <Link href="/admin/orders" className={linkClass('/admin/orders')} onClick={() => setIsOpen(false)}>
                <MdReceiptLong size={16} />
                Orders
              </Link>
              <Link href="/admin/promotions" className={linkClass('/admin/promotions')} onClick={() => setIsOpen(false)}>
                <FaTags size={16} />
                Promotions
              </Link>
              <Link href="/admin/customers" className={linkClass('/admin/customers')} onClick={() => setIsOpen(false)}>
                <FaUsers size={16} />
                Customers
              </Link>
              <Link href="/admin/logs" className={linkClass('/admin/logs')} onClick={() => setIsOpen(false)}>
                <FaShieldAlt size={16} />
                Logs
              </Link>
            </>
          )}
        </nav>

        {/* Logout */}
        <div className="px-3 py-4 border-t border-white/10">
          <button
            onClick={logout}
            className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm text-gray-300 hover:bg-white/10 hover:text-white transition-colors"
          >
            <FaSignOutAlt size={16} />
            Logout
          </button>
        </div>
      </aside>

      {/* Overlay — mobile only, คลิกเพื่อปิด */}
      {isOpen && (
        <div
          onClick={() => setIsOpen(false)}
          className="fixed inset-0 z-30 bg-black/50 md:hidden"
        />
      )}
    </>
  );
}
