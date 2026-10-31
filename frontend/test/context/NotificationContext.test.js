import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { fail, fakeApi, ok } from '../helpers/fakeApi';

// user ปัจจุบันเปลี่ยนได้ระหว่างเทส (login / logout / สลับบัญชี)
const auth = { user: null };
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => auth }));

// EventSource ปลอม — เก็บ instance ไว้ยิง event เองได้
// readyState ตามของจริง: CONNECTING = browser กำลังต่อใหม่เอง, CLOSED = ปิดถาวร (เช่น server ตอบ 401)
class FakeEventSource {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 2;
  static instances = [];
  constructor(url, opts) {
    this.url = url;
    this.opts = opts;
    this.listeners = {};
    this.closed = false;
    this.readyState = FakeEventSource.CONNECTING;
    FakeEventSource.instances.push(this);
  }
  addEventListener(type, fn) { this.listeners[type] = fn; }
  emit(type, data) { this.listeners[type]?.({ data: JSON.stringify(data) }); }
  close() { this.closed = true; this.readyState = FakeEventSource.CLOSED; }
  /** ต่อสำเร็จ */
  open() { this.readyState = FakeEventSource.OPEN; this.onopen?.(); }
  /** เน็ตหลุด / server ปิดตาม timeout — browser จะต่อใหม่เอง */
  drop() { this.readyState = FakeEventSource.CONNECTING; this.onerror?.(); }
  /** server ตอบไม่ใช่ 200 (เช่น 401 access token หมดอายุ) — browser ปิดถาวร */
  reject() { this.readyState = FakeEventSource.CLOSED; this.onerror?.(); }
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

afterEach(() => {
  vi.useRealTimers();
});

/** เดินเวลาไปข้างหน้า + รอ promise ที่ค้างอยู่ (เช่น request ที่ยิงตอน retry) */
const advance = (ms) => act(() => vi.advanceTimersByTimeAsync(ms));
const streams = () => FakeEventSource.instances;
const meCalls = (calls) => calls.filter(c => c.url === '/api/v1/users/me');

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

  // ───────────── สายหลุด / token หมดอายุ ─────────────

  it('a dropped connection is left to the browser\'s own reconnect (no extra stream, no extra request)', async () => {
    vi.useFakeTimers();
    auth.user = alice;
    const calls = fakeApi(() => ok([]));
    renderNotifications();

    act(() => streams()[0].drop());
    await advance(60_000);

    expect(streams()).toHaveLength(1);
    expect(streams()[0].closed).toBe(false);
    expect(meCalls(calls)).toHaveLength(0);
  });

  it('stream rejected (401 — access token expired): refreshes through the API, then opens a new stream', async () => {
    vi.useFakeTimers();
    auth.user = alice;
    const calls = fakeApi(() => ok([]));
    renderNotifications();

    act(() => streams()[0].reject());
    expect(streams()).toHaveLength(1);           // ยังไม่ต่อทันที — รอ backoff

    await advance(1000);

    expect(meCalls(calls)).toHaveLength(1);      // ผ่าน axios → interceptor refresh token ได้
    expect(streams()).toHaveLength(2);
    expect(streams()[1].opts).toEqual({ withCredentials: true });
  });

  it('the new stream still delivers notifications', async () => {
    vi.useFakeTimers();
    auth.user = alice;
    fakeApi(() => ok([]));
    const { result } = renderNotifications();
    await advance(0);

    act(() => streams()[0].reject());
    await advance(1000);
    act(() => streams()[1].emit('notification', note(9)));

    expect(result.current.notifications.map(n => n.id)).toEqual([9]);
    expect(result.current.unreadCount).toBe(1);
  });

  it('backs off 1s → 2s → 4s while it keeps failing, and resets after a successful connection', async () => {
    vi.useFakeTimers();
    auth.user = alice;
    fakeApi(() => ok([]));
    renderNotifications();

    act(() => streams()[0].reject());
    await advance(1000);
    expect(streams()).toHaveLength(2);

    act(() => streams()[1].reject());
    await advance(1999);
    expect(streams()).toHaveLength(2);           // รอบสองต้องรอ 2 วิ
    await advance(1);
    expect(streams()).toHaveLength(3);

    act(() => streams()[2].reject());
    await advance(3999);
    expect(streams()).toHaveLength(3);           // รอบสามต้องรอ 4 วิ
    await advance(1);
    expect(streams()).toHaveLength(4);

    act(() => streams()[3].open());              // ต่อสำเร็จ → นับใหม่
    act(() => streams()[3].reject());
    await advance(1000);
    expect(streams()).toHaveLength(5);
  });

  it('if the refresh request fails, it keeps retrying later instead of opening a stream that will be rejected', async () => {
    vi.useFakeTimers();
    auth.user = alice;
    let meResponses = [fail(503, 'down'), ok({ id: 7 })];
    const calls = fakeApi((config) => (config.url === '/api/v1/users/me' ? meResponses.shift() : ok([])));
    renderNotifications();

    act(() => streams()[0].reject());
    await advance(1000);
    expect(meCalls(calls)).toHaveLength(1);
    expect(streams()).toHaveLength(1);           // refresh ไม่ผ่าน — ยังไม่เปิดสาย

    await advance(2000);
    expect(meCalls(calls)).toHaveLength(2);
    expect(streams()).toHaveLength(2);
  });

  it('logout while a reconnect is waiting cancels it', async () => {
    vi.useFakeTimers();
    auth.user = alice;
    const calls = fakeApi(() => ok([]));
    const { rerender } = renderNotifications();

    act(() => streams()[0].reject());
    auth.user = null;
    rerender();
    await advance(60_000);

    expect(streams()).toHaveLength(1);
    expect(meCalls(calls)).toHaveLength(0);
  });
});
