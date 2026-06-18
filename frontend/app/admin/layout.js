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
      <div className="min-h-screen bg-[#EEF4FB] overflow-x-hidden">

        <Sidebar isOpen={isSidebarOpen} setIsOpen={setIsSidebarOpen} />

        {/* Content — เลื่อนขวา md:ml-64 บน desktop เพื่อเว้นที่ sidebar fixed */}
        <div className="flex flex-col min-h-screen md:ml-64">
          <Navbar setIsOpen={setIsSidebarOpen} />
          <main className="flex-1 p-4 md:p-6 overflow-y-auto">
            {children}
          </main>
        </div>

        <CartDrawer />
      </div>
    </NotificationProvider>
  );
}
