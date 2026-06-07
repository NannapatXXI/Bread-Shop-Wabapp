'use client';

import { createContext, useContext, useEffect, useRef, useState } from 'react';
import api from '@/lib/api';
import { useAuth } from './AuthContext';

const NotificationContext = createContext(null);

export function NotificationProvider({ children }) {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const eventSourceRef = useRef(null);

  // โหลด notifications จาก DB เมื่อ login
  useEffect(() => {
    if (!user) {
      setNotifications([]);
      setUnreadCount(0);
      return;
    }

    api.get('/api/v1/notifications')
      .then(res => {
        const data = res.data ?? [];
        setNotifications(data);
        setUnreadCount(data.filter(n => !n.isRead).length);
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
      } catch (_) {}
    });

    // ไม่ต้อง es.close() ใน onerror — browser จะ reconnect อัตโนมัติ
    es.onerror = () => {};

    eventSourceRef.current = es;

    return () => {
      es.close();
    };
  }, [user?.id]);

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

  return (
    <NotificationContext.Provider value={{ notifications, unreadCount, markAsRead, markAllAsRead }}>
      {children}
    </NotificationContext.Provider>
  );
}

export const useNotification = () => {
  const ctx = useContext(NotificationContext);
  if (!ctx) return { notifications: [], unreadCount: 0, markAsRead: () => {}, markAllAsRead: () => {} };
  return ctx;
};
