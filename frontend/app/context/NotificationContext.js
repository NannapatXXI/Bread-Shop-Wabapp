'use client';

import { createContext, useContext, useEffect, useRef, useState } from 'react';
import api from '@/lib/api';
import { useAuth } from './AuthContext';

const NotificationContext = createContext(null);
const NO_NOTIFICATIONS = [];

export function NotificationProvider({ children }) {
  const { user } = useAuth();
  const userId = user?.id;
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  // รายการข้างบนเป็นของ user คนไหน — ไม่ตรงกับคนที่ login อยู่ = ไม่แสดง (logout/สลับบัญชีไม่เห็นของคนก่อน)
  const [ownerId, setOwnerId] = useState(null);
  const eventSourceRef = useRef(null);

  // โหลด notifications จาก DB เมื่อ login
  useEffect(() => {
    if (!userId) return;

    api.get('/api/v1/notifications')
      .then(res => {
        const data = res.data ?? [];
        setNotifications(data);
        setUnreadCount(data.filter(n => !n.isRead).length);
        setOwnerId(userId);
      })
      .catch(() => {});

    // เชื่อม SSE (EventSource ส่ง cookie อัตโนมัติด้วย withCredentials)
    const sseUrl = `${process.env.NEXT_PUBLIC_API_URL}/api/v1/notifications/stream`;
    const es = new EventSource(sseUrl, { withCredentials: true });

    es.addEventListener('notification', (e) => {
      try {
        const newNoti = JSON.parse(e.data);
        setNotifications(prev => [newNoti, ...prev]);
        setUnreadCount(prev => prev + 1);
      } catch { /* ข้อมูลเสีย — ข้าม */ }
    });

    // ไม่ต้อง es.close() ใน onerror — browser จะ reconnect อัตโนมัติ
    es.onerror = () => {};

    eventSourceRef.current = es;

    return () => {
      es.close();
    };
  }, [userId]);

  const markAsRead = async (id) => {
    await api.patch(`/api/v1/notifications/${id}/read`);
    setNotifications(prev =>
      prev.map(n => (n.id === id ? { ...n, isRead: true } : n))
    );
    setUnreadCount(prev => Math.max(0, prev - 1));
  };

  const markAllAsRead = async () => {
    await api.patch('/api/v1/notifications/read-all');
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    setUnreadCount(0);
  };

  const visible = Boolean(userId) && ownerId === userId;

  return (
    <NotificationContext.Provider value={{
      notifications: visible ? notifications : NO_NOTIFICATIONS,
      unreadCount:   visible ? unreadCount : 0,
      markAsRead,
      markAllAsRead,
    }}>
      {children}
    </NotificationContext.Provider>
  );
}

export const useNotification = () => {
  const ctx = useContext(NotificationContext);
  if (!ctx) return { notifications: [], unreadCount: 0, markAsRead: () => {}, markAllAsRead: () => {} };
  return ctx;
};
