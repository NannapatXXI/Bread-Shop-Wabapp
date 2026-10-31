'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import api from '@/lib/api';
import { useAuth } from './AuthContext';

const NotificationContext = createContext(null);
const NO_NOTIFICATIONS = [];
const RETRY_BASE_MS = 1000;
const RETRY_MAX_MS = 30_000;

export function NotificationProvider({ children }) {
  const { user } = useAuth();
  const userId = user?.id;
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  // รายการข้างบนเป็นของ user คนไหน — ไม่ตรงกับคนที่ login อยู่ = ไม่แสดง (logout/สลับบัญชีไม่เห็นของคนก่อน)
  const [ownerId, setOwnerId] = useState(null);

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
    let es;
    let retryTimer;
    let attempt = 0;
    let stopped = false;

    const connect = () => {
      es = new EventSource(sseUrl, { withCredentials: true });
      es.onopen = () => { attempt = 0; };

      es.addEventListener('notification', (e) => {
        try {
          const newNoti = JSON.parse(e.data);
          setNotifications(prev => [newNoti, ...prev]);
          setUnreadCount(prev => prev + 1);
        } catch { /* ข้อมูลเสีย — ข้าม */ }
      });

      // เน็ตหลุด / server ปิดสายตาม timeout → readyState = CONNECTING, browser ต่อใหม่เอง ไม่ต้องทำอะไร
      // แต่ถ้า server ตอบไม่ใช่ 200 (เช่น 401 เพราะ access token 15 นาทีหมดอายุ) browser จะปิดถาวร (CLOSED)
      // และไม่ต่อใหม่อีก — ต้องต่อเอง ไม่งั้นแจ้งเตือน realtime หยุดเงียบ ๆ จนกว่าจะรีเฟรชหน้า
      es.onerror = () => {
        if (stopped || es.readyState !== EventSource.CLOSED) return;
        scheduleReconnect();
      };
    };

    // รอ 1, 2, 4, ... สูงสุด 30 วินาที — server ล่มอยู่ก็ไม่ยิงถี่
    const scheduleReconnect = () => {
      const delay = Math.min(RETRY_MAX_MS, RETRY_BASE_MS * 2 ** attempt);
      attempt += 1;
      retryTimer = setTimeout(reconnect, delay);
    };

    const reconnect = async () => {
      try {
        // EventSource ไม่ผ่าน axios interceptor — เรียก API ผ่าน axios ก่อน ให้ refresh token (ได้ cookie ใหม่)
        // ถ้า refresh token หมดอายุด้วย interceptor จะพาไปหน้า login เอง
        await api.get('/api/v1/users/me');
      } catch {
        if (!stopped) scheduleReconnect();
        return;
      }
      if (!stopped) connect();
    };

    connect();

    return () => {
      stopped = true;
      clearTimeout(retryTimer);
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
