'use client';

// app/admin/customers/page.js
// รายชื่อ customers ทั้งหมด (Low Stock ย้ายไปอยู่หน้า Products แล้ว)

import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { adminGetCustomers, adminUpdateUserRole, adminToggleBan, adminUpdateUsername, getMyOrders } from '@/services/auth.service';
import { exportCSV } from '@/lib/exportCSV';
import Spinner from '@/app/components/Spinner';
import { SkeletonRows } from '@/app/components/Skeleton';

const PAGE_SIZE = 5;

const AVATAR_COLORS = [
  'bg-purple-500', 'bg-blue-500',   'bg-emerald-500', 'bg-orange-500',
  'bg-pink-500',   'bg-teal-500',   'bg-indigo-500',  'bg-red-500',
  'bg-yellow-500', 'bg-cyan-500',   'bg-violet-500',  'bg-lime-500',
];
const getAvatarColor = (name = '') =>
  AVATAR_COLORS[(name.charCodeAt(0) ?? 0) % AVATAR_COLORS.length];

// แถบ filter tabs
const PROVIDER_TABS = [
  { key: 'ALL',    label: 'ทั้งหมด'      },
  { key: 'email',  label: 'Email'        },
  { key: 'google', label: 'Google'       },
  { key: 'USER',   label: 'User'         },
  { key: 'ADMIN',  label: 'Admin'        },
];

export default function AdminCustomersPage() {
  const [customers,      setCustomers]      = useState([]);
  const [loading,        setLoading]        = useState(true);
  const [search,         setSearch]         = useState('');
  const [filterTab,      setFilterTab]      = useState('ALL');
  const [page,           setPage]           = useState(1);
  const [selectedUser,   setSelectedUser]   = useState(null);
  const [modalTab,       setModalTab]       = useState('info'); // 'info' | 'orders'
  const [editRole,       setEditRole]       = useState('');
  const [editUsername,   setEditUsername]   = useState('');
  const [userOrders,     setUserOrders]     = useState([]);
  const [ordersLoading,  setOrdersLoading]  = useState(false);
  const [saving,         setSaving]         = useState(false);

  const openManage = (c) => {
    setSelectedUser(c);
    setEditRole(c.role);
    setEditUsername(c.username);
    setModalTab('info');
    setUserOrders([]);
  };

  const loadOrders = async (userId) => {
    setOrdersLoading(true);
    try {
      const res = await getMyOrders(userId);
      setUserOrders(res.data ?? []);
    } catch { setUserOrders([]); }
    finally { setOrdersLoading(false); }
  };

  const handleTabChange = (tab) => {
    setModalTab(tab);
    if (tab === 'orders' && userOrders.length === 0) loadOrders(selectedUser.id);
  };

  const handleSaveInfo = async () => {
    setSaving(true);
    try {
      const promises = [];
      if (editRole !== selectedUser.role)
        promises.push(adminUpdateUserRole(selectedUser.id, editRole));
      if (editUsername.trim() !== selectedUser.username)
        promises.push(adminUpdateUsername(selectedUser.id, editUsername.trim()));
      await Promise.all(promises);
      setCustomers(prev => prev.map(c =>
        c.id === selectedUser.id
          ? { ...c, role: editRole, username: editUsername.trim() }
          : c
      ));
      toast.success('อัปเดตสำเร็จ');
      setSelectedUser(null);
    } catch {
      toast.error('อัปเดตไม่สำเร็จ');
    } finally { setSaving(false); }
  };

  const handleToggleBan = async () => {
    setSaving(true);
    try {
      await adminToggleBan(selectedUser.id);
      const newActive = !selectedUser.isActive;
      setCustomers(prev => prev.map(c =>
        c.id === selectedUser.id ? { ...c, isActive: newActive } : c
      ));
      setSelectedUser(prev => ({ ...prev, isActive: newActive }));
      toast.success(newActive ? 'ปลดระงับบัญชีสำเร็จ' : 'ระงับบัญชีสำเร็จ');
    } catch {
      toast.error('ดำเนินการไม่สำเร็จ');
    } finally { setSaving(false); }
  };

  useEffect(() => {
    adminGetCustomers()
      .then(r => setCustomers(r.data ?? []))
      .catch(() => toast.error('โหลดข้อมูลไม่สำเร็จ'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { setPage(1); }, [search, filterTab]);

  // ── Derived ──────────────────────────────────────────────────
  const filtered = customers.filter(c => {
    const matchSearch =
      c.username.toLowerCase().includes(search.toLowerCase()) ||
      c.email.toLowerCase().includes(search.toLowerCase());
    const matchTab =
      filterTab === 'ALL'    ? true :
      filterTab === 'google' ? c.provider === 'google' :
      filterTab === 'email'  ? c.provider !== 'google' :
      filterTab === 'USER'   ? c.role === 'USER' :
      filterTab === 'ADMIN'  ? c.role === 'ADMIN' :
      true;
    return matchSearch && matchTab;
  });
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const paginated  = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const fmt = (dt) => dt
    ? new Date(dt).toLocaleDateString('th-TH', { dateStyle: 'medium' })
    : '-';

  return (
    <div className="bg-[#EEF4FB] min-h-screen p-6">

      {/* ── Header ──────────────────────────────────────────── */}
      <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">Customers</h1>
          <p className="text-gray-500 text-sm mt-0.5">รายชื่อลูกค้าทั้งหมดในระบบ</p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            onClick={() => exportCSV(
              customers,
              ['ID', 'ชื่อผู้ใช้', 'Email', 'Login ด้วย', 'Role', 'สมัครเมื่อ', 'Orders'],
              ['id', 'username', 'email', 'provider', 'role', 'createdAt', 'orderCount'],
              'customers'
            )}
            className="flex items-center gap-2 px-4 py-2 border border-gray-300 bg-white rounded-xl text-sm font-medium text-gray-600 hover:bg-gray-50 transition">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Export
          </button>
          <button className="flex items-center gap-2 px-4 py-2 bg-[#0B1F33] hover:bg-blue-900 text-[#A8CEFF] rounded-xl text-sm font-semibold transition whitespace-nowrap">
            <span className="text-base leading-none">+</span>
            เพิ่มลูกค้า
          </button>
        </div>
      </div>

      {/* ── Stats Cards ─────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-5">

        {/* ลูกค้าทั้งหมด */}
        <div className="bg-white rounded-2xl p-5 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center flex-shrink-0">
            <svg className="w-6 h-6 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </div>
          <div>
            <p className="text-3xl font-bold text-gray-800">{customers.length}</p>
            <p className="text-sm text-gray-500 mt-0.5">ลูกค้าทั้งหมด</p>
          </div>
        </div>

        {/* Google Login */}
        <div className="bg-white rounded-2xl p-5 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-emerald-50 flex items-center justify-center flex-shrink-0">
            <svg className="w-6 h-6 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
            </svg>
          </div>
          <div>
            <p className="text-3xl font-bold text-emerald-600">
              {customers.filter(c => c.provider === 'google').length}
            </p>
            <p className="text-sm text-gray-500 mt-0.5">Google Login</p>
            <span className="inline-block mt-1 text-xs bg-emerald-100 text-emerald-600 px-2 py-0.5 rounded-full font-medium">
              OAuth 2.0
            </span>
          </div>
        </div>

        {/* Email Login */}
        <div className="bg-white rounded-2xl p-5 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center flex-shrink-0">
            <svg className="w-6 h-6 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          </div>
          <div>
            <p className="text-3xl font-bold text-blue-600">
              {customers.filter(c => c.provider !== 'google').length}
            </p>
            <p className="text-sm text-gray-500 mt-0.5">Email Login</p>
            <span className="inline-block mt-1 text-xs bg-blue-100 text-blue-600 px-2 py-0.5 rounded-full font-medium">
              Credentials
            </span>
          </div>
        </div>
      </div>

      {/* ── Filter Bar ──────────────────────────────────────── */}
      <div className="bg-white rounded-xl shadow-sm px-4 py-3 mb-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
        {/* Tabs — scroll horizontally on mobile */}
        <div className="flex gap-1 overflow-x-auto flex-1 min-w-0">
          {PROVIDER_TABS.map(t => (
            <button key={t.key}
              onClick={() => setFilterTab(t.key)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition whitespace-nowrap shrink-0
                ${filterTab === t.key
                  ? 'bg-[#0B1F33] text-white'
                  : 'text-gray-500 hover:bg-gray-100'}`}>
              {t.label}
              <span className={`text-xs px-1.5 py-0.5 rounded-full font-bold
                ${filterTab === t.key ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-500'}`}>
                {t.key === 'ALL'    ? customers.length
                  : t.key === 'google' ? customers.filter(c => c.provider === 'google').length
                  : t.key === 'email'  ? customers.filter(c => c.provider !== 'google').length
                  : t.key === 'USER'   ? customers.filter(c => c.role === 'USER').length
                  : t.key === 'ADMIN'  ? customers.filter(c => c.role === 'ADMIN').length
                  : 0}
              </span>
            </button>
          ))}
        </div>

        {/* Search */}
        <div className="relative shrink-0">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
            fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="ค้นหาลูกค้า..."
            className="pl-9 pr-4 py-2 border rounded-xl text-sm w-full sm:w-52 focus:outline-none focus:ring-2 focus:ring-[#0B1F33]/30" />
        </div>
      </div>

      {/* ── Table ────────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl shadow-sm overflow-x-auto">
        {loading ? (
          <table className="w-full text-sm min-w-[600px]">
            <tbody><SkeletonRows rows={8} cols={7} /></tbody>
          </table>
        ) : (
          <>
            <table className="w-full text-sm min-w-[600px]">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">ลูกค้า</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">Email</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">Login ด้วย</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">สมัครเมื่อ</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">Orders</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">Role</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">จัดการ</th>
                </tr>
              </thead>
              <tbody>
                {paginated.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center py-12 text-gray-400">ไม่พบลูกค้า</td>
                  </tr>
                ) : paginated.map(c => {
                  const avatarColor = getAvatarColor(c.username);
                  return (
                    <tr key={c.id} className="border-b border-gray-50 hover:bg-gray-50/80 transition">

                      {/* Avatar + Name */}
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-3">
                          <div className={`w-9 h-9 rounded-full ${avatarColor} flex items-center justify-center
                            text-white text-sm font-bold flex-shrink-0 ${!c.isActive ? 'opacity-50' : ''}`}>
                            {c.username?.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <span className={`font-semibold ${c.isActive ? 'text-gray-800' : 'text-gray-400 line-through'}`}>{c.username}</span>
                            {!c.isActive && (
                              <span className="ml-2 text-xs px-1.5 py-0.5 rounded bg-red-100 text-red-500 font-medium">ถูกระงับ</span>
                            )}
                          </div>
                        </div>
                      </td>

                      <td className="px-4 py-3.5 text-gray-500">{c.email}</td>

                      {/* Login type */}
                      <td className="px-4 py-3.5 text-center">
                        {c.provider === 'google' ? (
                          <span className="inline-flex items-center gap-1 text-xs px-3 py-1 rounded-full font-semibold bg-red-100 text-red-600">
                            <span className="font-bold text-sm leading-none">G</span> Google
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded-full font-medium bg-gray-100 text-gray-600">
                            <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                            </svg>
                            Email
                          </span>
                        )}
                      </td>

                      <td className="px-4 py-3.5 text-center text-gray-500">{fmt(c.createdAt)}</td>

                      {/* Orders */}
                      <td className="px-4 py-3.5 text-center">
                        <span className={`text-base font-bold ${c.orderCount > 0 ? 'text-blue-600' : 'text-gray-400'}`}>
                          {c.orderCount}
                        </span>
                      </td>

                      {/* Role */}
                      <td className="px-4 py-3.5 text-center">
                        <span className={`text-xs px-3 py-1 rounded-full font-semibold
                          ${c.role === 'ADMIN'
                            ? 'bg-purple-100 text-purple-700'
                            : 'bg-emerald-100 text-emerald-700'}`}>
                          {c.role}
                        </span>
                      </td>

                      {/* จัดการ */}
                      <td className="px-4 py-3.5 text-center">
                        <button onClick={() => openManage(c)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#0B1F33] text-[#A8CEFF] text-xs font-semibold rounded-lg hover:bg-blue-900 transition">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                              d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                          </svg>
                          จัดการ
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* ── Pagination ────────────────────────────── */}
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
              <p className="text-xs text-gray-500">
                แสดง {filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, filtered.length)} จาก {filtered.length} ลูกค้า
              </p>
              <div className="flex items-center gap-1">
                <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}
                  className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 disabled:opacity-30 transition">
                  ‹
                </button>

                {Array.from({ length: totalPages }, (_, i) => i + 1)
                  .filter(p => p === 1 || p === totalPages || Math.abs(p - page) <= 1)
                  .reduce((acc, p, idx, arr) => {
                    if (idx > 0 && p - arr[idx - 1] > 1) acc.push('...');
                    acc.push(p);
                    return acc;
                  }, [])
                  .map((p, i) =>
                    p === '...'
                      ? <span key={`dot-${i}`} className="w-8 h-8 flex items-center justify-center text-gray-400 text-xs">…</span>
                      : <button key={p} onClick={() => setPage(p)}
                          className={`w-8 h-8 flex items-center justify-center rounded-lg text-sm transition
                            ${page === p
                              ? 'bg-[#0B1F33] text-white font-semibold'
                              : 'text-gray-600 hover:bg-gray-100'}`}>
                          {p}
                        </button>
                  )}

                <button onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages}
                  className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 disabled:opacity-30 transition">
                  ›
                </button>
              </div>
            </div>
          </>
        )}
      </div>
      {/* ── Modal: จัดการ User ─────────────────────────────── */}
      {selectedUser && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4"
          onClick={e => e.target === e.currentTarget && setSelectedUser(null)}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">

            {/* Header */}
            <div className="flex justify-between items-center px-6 pt-5 pb-4 border-b border-gray-100">
              <h3 className="font-bold text-gray-800 text-lg">จัดการผู้ใช้</h3>
              <button onClick={() => setSelectedUser(null)} className="text-gray-400 hover:text-gray-600 text-xl leading-none">✕</button>
            </div>

            {/* Avatar summary bar */}
            <div className="flex items-center gap-3 px-6 py-4 bg-gray-50">
              <div className={`w-12 h-12 rounded-full ${getAvatarColor(selectedUser.username)} flex items-center justify-center text-white text-lg font-bold flex-shrink-0`}>
                {selectedUser.username?.charAt(0).toUpperCase()}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-bold text-gray-800 truncate">{selectedUser.username}</p>
                <p className="text-xs text-gray-500">{selectedUser.email}</p>
              </div>
              {/* Ban badge */}
              {!selectedUser.isActive && (
                <span className="text-xs px-2.5 py-1 rounded-full font-semibold bg-red-100 text-red-600">ถูกระงับ</span>
              )}
              <div className="text-center flex-shrink-0">
                <p className={`text-xl font-bold ${selectedUser.orderCount > 0 ? 'text-blue-600' : 'text-gray-400'}`}>{selectedUser.orderCount}</p>
                <p className="text-xs text-gray-400">orders</p>
              </div>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-gray-100 px-6">
              {[{ key: 'info', label: 'ข้อมูล & สิทธิ์' }, { key: 'orders', label: 'ประวัติออเดอร์' }].map(t => (
                <button key={t.key} onClick={() => handleTabChange(t.key)}
                  className={`py-3 px-4 text-sm font-medium border-b-2 transition -mb-px
                    ${modalTab === t.key ? 'border-[#0B1F33] text-[#0B1F33]' : 'border-transparent text-gray-400 hover:text-gray-600'}`}>
                  {t.label}
                </button>
              ))}
            </div>

            <div className="p-6">

              {/* ── Tab: ข้อมูล & สิทธิ์ ── */}
              {modalTab === 'info' && (
                <div className="space-y-4">
                  {/* แก้ username */}
                  <div>
                    <label className="text-xs font-semibold text-gray-600 block mb-1.5">Username</label>
                    <input value={editUsername} onChange={e => setEditUsername(e.target.value)}
                      className="w-full border rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]/30" />
                  </div>

                  {/* Email (read-only) */}
                  <div>
                    <label className="text-xs font-semibold text-gray-600 block mb-1.5">Email <span className="font-normal text-gray-400">(แก้ไขไม่ได้)</span></label>
                    <div className="w-full border border-dashed border-gray-200 rounded-xl px-4 py-2.5 text-sm text-gray-400 bg-gray-50">
                      {selectedUser.email}
                    </div>
                  </div>

                  {/* Role */}
                  <div>
                    <label className="text-xs font-semibold text-gray-600 block mb-1.5">Role</label>
                    <div className="flex gap-2">
                      {['USER', 'ADMIN'].map(r => (
                        <button key={r} onClick={() => setEditRole(r)}
                          className={`flex-1 py-2 rounded-xl text-sm font-semibold border-2 transition
                            ${editRole === r
                              ? r === 'ADMIN' ? 'bg-purple-500 border-purple-500 text-white' : 'bg-emerald-500 border-emerald-500 text-white'
                              : 'bg-white border-gray-200 text-gray-400 hover:border-gray-300'}`}>
                          {r}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Ban / Unban */}
                  <div className={`flex items-center justify-between rounded-xl p-4 border
                    ${selectedUser.isActive ? 'border-red-100 bg-red-50' : 'border-green-100 bg-green-50'}`}>
                    <div>
                      <p className={`text-sm font-semibold ${selectedUser.isActive ? 'text-red-700' : 'text-green-700'}`}>
                        {selectedUser.isActive ? 'ระงับบัญชี' : 'ปลดระงับบัญชี'}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {selectedUser.isActive ? 'user จะ login ไม่ได้ทันที' : 'user จะกลับมา login ได้'}
                      </p>
                    </div>
                    <button onClick={handleToggleBan} disabled={saving}
                      className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition disabled:opacity-50
                        ${selectedUser.isActive
                          ? 'bg-red-500 hover:bg-red-600 text-white'
                          : 'bg-green-500 hover:bg-green-600 text-white'}`}>
                      {selectedUser.isActive ? 'ระงับ' : 'ปลดระงับ'}
                    </button>
                  </div>

                  {/* Actions */}
                  <div className="flex gap-3 pt-1">
                    <button onClick={() => setSelectedUser(null)}
                      className="flex-1 py-2.5 border rounded-xl text-gray-600 hover:bg-gray-50 text-sm font-medium transition">
                      ยกเลิก
                    </button>
                    <button onClick={handleSaveInfo} disabled={saving}
                      className="flex-1 py-2.5 bg-[#0B1F33] text-[#A8CEFF] rounded-xl font-semibold text-sm hover:bg-blue-900 disabled:opacity-50 transition">
                      {saving
                        ? <span className="flex items-center justify-center gap-2"><Spinner size={16} color="#A8CEFF" /> กำลังบันทึก...</span>
                        : 'บันทึก'}
                    </button>
                  </div>
                </div>
              )}

              {/* ── Tab: ประวัติออเดอร์ ── */}
              {modalTab === 'orders' && (
                <div>
                  {ordersLoading ? (
                    <div className="py-4 space-y-2">
                      {Array.from({ length: 3 }).map((_, i) => (
                        <div key={i} className="animate-pulse flex items-center gap-3 p-3 rounded-lg bg-gray-50">
                          <div className="w-10 h-10 bg-gray-200 rounded-lg flex-shrink-0" />
                          <div className="flex-1 space-y-2">
                            <div className="h-3 bg-gray-200 rounded w-1/3" />
                            <div className="h-3 bg-gray-200 rounded w-1/2" />
                          </div>
                          <div className="h-5 w-16 bg-gray-200 rounded-full" />
                        </div>
                      ))}
                    </div>
                  ) : userOrders.length === 0 ? (
                    <p className="text-center text-gray-400 py-8">ยังไม่มีออเดอร์</p>
                  ) : (
                    <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                      {userOrders.map(o => {
                        const statusColor =
                          o.status === 'DELIVERED' ? 'bg-green-100 text-green-700' :
                          o.status === 'CANCELLED' ? 'bg-red-100 text-red-700' :
                          o.status === 'SHIPPED'   ? 'bg-purple-100 text-purple-700' :
                          'bg-orange-100 text-orange-700';
                        const statusLabel = { PENDING: 'รอดำเนินการ', PROCESSING: 'กำลังเตรียม', SHIPPED: 'จัดส่งแล้ว', DELIVERED: 'ได้รับแล้ว', CANCELLED: 'ยกเลิก' };
                        return (
                          <div key={o.id} className="flex items-center justify-between rounded-xl border border-gray-100 px-4 py-3">
                            <div>
                              <p className="text-xs font-mono font-semibold text-indigo-600">{o.orderNo}</p>
                              <p className="text-xs text-gray-400 mt-0.5">
                                {o.orderLines?.map(l => l.productName).join(', ')}
                              </p>
                            </div>
                            <div className="text-right flex-shrink-0 ml-3">
                              <p className="text-sm font-bold text-gray-800">฿{Number(o.totalAmount).toLocaleString()}</p>
                              <span className={`text-xs px-2 py-0.5 rounded-full font-medium mt-0.5 inline-block ${statusColor}`}>
                                {statusLabel[o.status] ?? o.status}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
