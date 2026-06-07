'use client';

import { useEffect, useRef, useState } from 'react';
import { FaBell } from 'react-icons/fa';
import Link from 'next/link';
import { useNotification } from '../context/NotificationContext';

const STATUS_LABEL = {
  PENDING:    'รอดำเนินการ',
  CONFIRMED:  'ยืนยันแล้ว',
  PROCESSING: 'กำลังเตรียมสินค้า',
  SHIPPED:    'จัดส่งแล้ว',
  DELIVERED:  'ส่งถึงแล้ว',
  CANCELLED:  'ยกเลิก',
  REFUNDED:   'คืนเงินแล้ว',
};

const STATUS_COLOR = {
  PENDING:    'bg-yellow-100 text-yellow-700',
  CONFIRMED:  'bg-blue-100 text-blue-700',
  PROCESSING: 'bg-orange-100 text-orange-700',
  SHIPPED:    'bg-indigo-100 text-indigo-700',
  DELIVERED:  'bg-green-100 text-green-700',
  CANCELLED:  'bg-red-100 text-red-700',
  REFUNDED:   'bg-gray-100 text-gray-700',
};

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleString('th-TH', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function NotificationBell() {
  const { notifications, unreadCount, markAsRead, markAllAsRead } = useNotification();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  // ปิด dropdown เมื่อคลิกข้างนอก
  useEffect(() => {
    function handleClickOutside(e) {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleItemClick = (noti) => {
    if (!noti.isRead) markAsRead(noti.id);
    setOpen(false);
  };

  return (
    <div className="relative" ref={ref}>
      {/* ── ปุ่มกระดิ่ง ── */}
      <button
        onClick={() => setOpen(prev => !prev)}
        className="relative p-1 hover:text-blue-600 transition-colors"
        title="การแจ้งเตือน"
      >
        <FaBell size={22} className="text-gray-600 hover:text-[#0B1F33] transition-colors" />
        {unreadCount > 0 && (
          <span className="
            absolute -top-1 -right-1
            bg-red-500 text-white text-xs font-bold
            rounded-full min-w-[18px] h-[18px] px-0.5
            flex items-center justify-center
          ">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* ── Dropdown ── */}
      {open && (
        <div className="
          absolute right-0 mt-2 w-80 bg-white rounded-xl shadow-xl border border-gray-100
          z-50 overflow-hidden
        ">
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <span className="font-semibold text-gray-800 text-sm">การแจ้งเตือน</span>
            {unreadCount > 0 && (
              <button
                onClick={markAllAsRead}
                className="text-xs text-indigo-500 hover:text-indigo-700 transition-colors"
              >
                อ่านทั้งหมด
              </button>
            )}
          </div>

          {/* List */}
          <ul className="max-h-96 overflow-y-auto divide-y divide-gray-50">
            {notifications.length === 0 ? (
              <li className="px-4 py-8 text-center text-gray-400 text-sm">
                ยังไม่มีการแจ้งเตือน
              </li>
            ) : (
              notifications.map(noti => (
                <li key={noti.id}>
                  <Link
                    href="/history"
                    onClick={() => handleItemClick(noti)}
                    className={`
                      flex flex-col gap-1 px-4 py-3 hover:bg-gray-50 transition-colors
                      ${!noti.isRead ? 'bg-indigo-50/60' : ''}
                    `}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-semibold text-gray-500">{noti.orderNo}</span>
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${STATUS_COLOR[noti.newStatus] ?? 'bg-gray-100 text-gray-600'}`}>
                        {STATUS_LABEL[noti.newStatus] ?? noti.newStatus}
                      </span>
                    </div>
                    <p className="text-sm text-gray-700 leading-snug">{noti.message}</p>
                    <span className="text-[11px] text-gray-400">{formatDate(noti.createdAt)}</span>
                    {!noti.isRead && (
                      <span className="w-2 h-2 rounded-full bg-indigo-500 self-end mt-0.5" />
                    )}
                  </Link>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
