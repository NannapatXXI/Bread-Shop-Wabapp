'use client';

import ShopHeader    from '../components/ShopHeader';
import ShopBottomNav from '../components/ShopBottomNav';
import CartDrawer    from '../components/CartDrawer';
import { NotificationProvider } from '../context/NotificationContext';

export default function AppLayout({ children }) {
  return (
    <NotificationProvider>
      <ShopHeader />
      <main className="wrap shop">{children}</main>
      <CartDrawer />
      <ShopBottomNav />
    </NotificationProvider>
  );
}
