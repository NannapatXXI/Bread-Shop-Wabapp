// app/(user)/layout.js
'use client';

import { useState } from 'react';
import Sidebar    from '../components/Sidebar';
import Navbar     from '../components/Navbar';
import CartDrawer from '../components/CartDrawer';
import { NotificationProvider } from '../context/NotificationContext';

export default function AppLayout({ children }) {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  return (
    <NotificationProvider>
    <div className="flex h-screen bg-[#EEF4FB]">

      {/* Sidebar overlay (mobile) */}
      {isSidebarOpen && (
        <div
          onClick={() => setIsSidebarOpen(false)}
          className="fixed inset-0 bg-black opacity-50 z-20 md:hidden"
        />
      )}

      <Sidebar isOpen={isSidebarOpen} setIsOpen={setIsSidebarOpen} />

      <div className="flex-1 flex flex-col min-w-0">
        <Navbar setIsOpen={setIsSidebarOpen} />
        <main className="flex-1 p-6 overflow-y-auto">
          {children}
        </main>
      </div>

      {/* CartDrawer วางนอก flex ปกติ เพราะเป็น fixed overlay */}
      <CartDrawer />
    </div>
    </NotificationProvider>
  );
}