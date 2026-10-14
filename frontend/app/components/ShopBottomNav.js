'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCart } from '../CartContext';
import Icon from './Icon';

const TABS = [
  ['/home', 'home', 'หน้าแรก'],
  ['/product', 'shop', 'สินค้า'],
  null,
  ['/history', 'clock', 'ออเดอร์'],
  ['/profile', 'user', 'ฉัน'],
];

export default function ShopBottomNav() {
  const pathname = usePathname();
  const { cartCount, isOpen, toggleCart } = useCart();

  return (
    <nav className="bottom-nav" aria-label="เมนูหลัก (มือถือ)">
      {TABS.map((tab) => {
        if (!tab) {
          return (
            <a key="cart" href="#" role="button" className={isOpen ? 'on' : undefined}
              onClick={(e) => { e.preventDefault(); toggleCart(); }}>
              <Icon name="cart" />
              {cartCount > 0 && <span className="count">{cartCount > 99 ? '99+' : cartCount}</span>}
              <span>ตะกร้า</span>
            </a>
          );
        }
        const [href, icon, label] = tab;
        const on = !isOpen && pathname.startsWith(href);
        return (
          <Link key={href} href={href} className={on ? 'on' : undefined} aria-current={on ? 'page' : undefined}>
            <Icon name={icon} /><span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
