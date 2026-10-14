'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useNotification } from '../context/NotificationContext';
import Icon from './Icon';
import StatusBadge from './StatusBadge';

const STATUS_LABEL = {
  PENDING:    'รอดำเนินการ',
  CONFIRMED:  'ยืนยันแล้ว',
  PROCESSING: 'กำลังเตรียมสินค้า',
  SHIPPED:    'จัดส่งแล้ว',
  DELIVERED:  'ส่งถึงแล้ว',
  CANCELLED:  'ยกเลิก',
  REFUNDED:   'คืนเงินแล้ว',
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
    <div style={{ position: 'relative' }} ref={ref}>
      <button className="icon-btn" onClick={() => setOpen(prev => !prev)}
        aria-label={`การแจ้งเตือน${unreadCount > 0 ? ` ${unreadCount} รายการใหม่` : ''}`} aria-expanded={open}>
        <Icon name="bell" />
        {unreadCount > 0 && <span className="count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>

      {open && (
        <div className="pop">
          <div className="pop-head">
            <span>การแจ้งเตือน</span>
            {unreadCount > 0 && (
              <button className="link" style={{ background: 'none', border: 0, padding: 0, fontSize: 13 }} onClick={markAllAsRead}>
                อ่านทั้งหมด
              </button>
            )}
          </div>
          <ul className="pop-list">
            {notifications.length === 0 ? (
              <li className="muted" style={{ padding: '32px 16px', textAlign: 'center', fontSize: 14 }}>ยังไม่มีการแจ้งเตือน</li>
            ) : (
              notifications.map(noti => (
                <li key={noti.id}>
                  <Link href="/history" onClick={() => handleItemClick(noti)} className={`pop-item${!noti.isRead ? ' unread' : ''}`}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                      <span className="mono" style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 12, color: 'var(--blue)' }}>{noti.orderNo}</span>
                      <StatusBadge status={noti.newStatus} label={STATUS_LABEL[noti.newStatus] ?? noti.newStatus} />
                    </div>
                    <span>{noti.message}</span>
                    <span className="faint" style={{ fontSize: 12 }}>{formatDate(noti.createdAt)}</span>
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
