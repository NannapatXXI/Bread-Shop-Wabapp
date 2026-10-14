import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { fakeApi, ok } from '../helpers/fakeApi';

// user ปัจจุบันเปลี่ยนได้ระหว่างเทส (login / logout / สลับบัญชี)
const auth = { user: null };
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => auth }));

// EventSource ปลอม — เก็บ instance ไว้ยิง event 'notification' เองได้
class FakeEventSource {
  static instances = [];
  constructor(url, opts) {
    this.url = url;
    this.opts = opts;
    this.listeners = {};
    this.closed = false;
    FakeEventSource.instances.push(this);
  }
  addEventListener(type, fn) { this.listeners[type] = fn; }
  emit(type, data) { this.listeners[type]?.({ data: JSON.stringify(data) }); }
  close() { this.closed = true; }
}
globalThis.EventSource = FakeEventSource;

const { NotificationProvider, useNotification } = await import('@/app/context/NotificationContext');

const note = (id, isRead = false) => ({ id, message: `ออเดอร์ ${id}`, isRead });
const alice = { id: 7, email: 'alice@example.com' };
const bob = { id: 8, email: 'bob@example.com' };

const renderNotifications = () => renderHook(() => useNotification(), { wrapper: NotificationProvider });

beforeEach(() => {
  auth.user = null;
  FakeEventSource.instances = [];
});

describe('NotificationContext', () => {
  it('logged out: nothing loaded, no stream opened', () => {
    const calls = fakeApi(() => ok([]));

    const { result } = renderNotifications();

    expect(result.current.notifications).toEqual([]);
    expect(result.current.unreadCount).toBe(0);
    expect(calls).toHaveLength(0);
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it('logged in: loads notifications and counts unread ones', async () => {
    auth.user = alice;
    fakeApi(() => ok([note(1), note(2, true), note(3)]));

    const { result } = renderNotifications();

    await waitFor(() => expect(result.current.notifications).toHaveLength(3));
    expect(result.current.unreadCount).toBe(2);
    expect(FakeEventSource.instances[0].opts).toEqual({ withCredentials: true });
  });

  it('a pushed notification appears on top and bumps the unread count', async () => {
    auth.user = alice;
    fakeApi(() => ok([note(1, true)]));
    const { result } = renderNotifications();
    await waitFor(() => expect(result.current.notifications).toHaveLength(1));

    act(() => FakeEventSource.instances[0].emit('notification', note(9)));

    expect(result.current.notifications.map(n => n.id)).toEqual([9, 1]);
    expect(result.current.unreadCount).toBe(1);
  });

  it('logout hides the previous user\'s notifications and closes the stream', async () => {
    auth.user = alice;
    fakeApi(() => ok([note(1), note(2)]));
    const { result, rerender } = renderNotifications();
    await waitFor(() => expect(result.current.unreadCount).toBe(2));

    auth.user = null;
    rerender();

    expect(result.current.notifications).toEqual([]);
    expect(result.current.unreadCount).toBe(0);
    expect(FakeEventSource.instances[0].closed).toBe(true);
  });

  it('switching account never shows the previous user\'s notifications, even before the new ones load', async () => {
    auth.user = alice;
    let release;
    fakeApi(() => (auth.user === alice
      ? ok([note(1), note(2)])
      : new Promise((resolve) => { release = () => resolve(ok([note(50)])); })));
    const { result, rerender } = renderNotifications();
    await waitFor(() => expect(result.current.unreadCount).toBe(2));

    auth.user = bob;
    rerender();

    // ของ Bob ยังโหลดไม่เสร็จ — ต้องไม่เห็นของ Alice ค้างอยู่
    expect(result.current.notifications).toEqual([]);
    expect(result.current.unreadCount).toBe(0);

    await act(async () => release());
    await waitFor(() => expect(result.current.notifications.map(n => n.id)).toEqual([50]));
  });

  it('markAsRead calls the API and lowers the unread count', async () => {
    auth.user = alice;
    const calls = fakeApi((config) => (config.method === 'patch' ? ok(null) : ok([note(1), note(2)])));
    const { result } = renderNotifications();
    await waitFor(() => expect(result.current.unreadCount).toBe(2));

    await act(() => result.current.markAsRead(1));

    expect(calls.at(-1)).toMatchObject({ method: 'patch', url: '/api/v1/notifications/1/read' });
    expect(result.current.unreadCount).toBe(1);
    expect(result.current.notifications.find(n => n.id === 1).isRead).toBe(true);
  });
});
