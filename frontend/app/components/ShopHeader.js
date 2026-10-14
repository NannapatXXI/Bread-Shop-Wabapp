'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCart } from '../CartContext';
import { useAuth } from '../context/AuthContext';
import Brand from './Brand';
import Icon from './Icon';
import NotificationBell from './NotificationBell';

const NAV = [
  ['/home', 'หน้าแรก'],
  ['/product', 'สินค้า'],
  ['/history', 'ประวัติการสั่งซื้อ'],
];

export const isAdmin = (user) => user?.roles?.some(r => r.authority === 'ROLE_ADMIN');

function AccountMenu({ user, logout, active }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, []);

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="avatar" onClick={() => setOpen(o => !o)} aria-haspopup="menu" aria-expanded={open}
        aria-label={`บัญชี ${user?.username ?? ''}`}
        style={active || open ? { boxShadow: '0 0 0 2px var(--blue)' } : undefined}>
        {user?.username?.charAt(0).toUpperCase() ?? ''}
      </button>
      {open && (
        <div className="pop" role="menu" style={{ width: 240 }}>
          <div className="pop-head" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: 0 }}>
            <span>{user?.username}</span>
            <span className="muted" style={{ fontWeight: 400, fontSize: 13, overflowWrap: 'anywhere' }}>{user?.email}</span>
          </div>
          <Link className="menu-item" role="menuitem" href="/profile" onClick={() => setOpen(false)}>
            <Icon name="user" size="sm" />โปรไฟล์ของฉัน
          </Link>
          <Link className="menu-item" role="menuitem" href="/history" onClick={() => setOpen(false)}>
            <Icon name="receipt" size="sm" />ประวัติการสั่งซื้อ
          </Link>
          <button className="menu-item" role="menuitem" style={{ color: 'var(--red)', borderTop: '1px solid var(--line)' }}
            onClick={() => { setOpen(false); logout(); }}>
            <Icon name="logout" size="sm" />ออกจากระบบ
          </button>
        </div>
      )}
    </div>
  );
}

export default function ShopHeader() {
  const pathname = usePathname();
  const { cartCount, toggleCart } = useCart();
  const { user, logout } = useAuth();

  return (
    <header className="top">
      <div className="wrap">
        <Brand href="/home" />
        <nav className="main" aria-label="เมนูหลัก">
          {NAV.map(([href, label]) => (
            <Link key={href} href={href} className={pathname.startsWith(href) ? 'on' : undefined}
              aria-current={pathname.startsWith(href) ? 'page' : undefined}>{label}</Link>
          ))}
        </nav>
        <div className="tools">
          {isAdmin(user) && (
            <Link className="admin-link" href="/admin/dashbord"><Icon name="grid" size="sm" />หลังร้าน</Link>
          )}
          <NotificationBell />
          <button className="icon-btn cart-btn" onClick={toggleCart} aria-label={`ตะกร้า ${cartCount} ชิ้น`}>
            <Icon name="cart" />
            {cartCount > 0 && <span className="count">{cartCount > 99 ? '99+' : cartCount}</span>}
          </button>
          <AccountMenu user={user} logout={logout} active={pathname.startsWith('/profile')} />
        </div>
      </div>
    </header>
  );
}
