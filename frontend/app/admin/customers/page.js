'use client';

// app/admin/customers/page.js
// รายชื่อ customers ทั้งหมด (Low Stock ย้ายไปอยู่หน้า Products แล้ว)

import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { adminGetCustomers, adminUpdateUserRole, adminSetCustomerActive, adminUpdateUsername, adminGetCustomerOrders } from '@/services/auth.service';
import { exportCSV } from '@/lib/exportCSV';
import Spinner from '@/app/components/Spinner';
import { SkeletonRows } from '@/app/components/Skeleton';
import Icon from '@/app/components/Icon';
import StatusBadge from '@/app/components/StatusBadge';

const PAGE_SIZE = 5;

// แถบ filter tabs
const PROVIDER_TABS = [
  { key: 'ALL',    label: 'ทั้งหมด'      },
  { key: 'email',  label: 'Email'        },
  { key: 'google', label: 'Google'       },
  { key: 'USER',   label: 'ลูกค้า'       },
  { key: 'ADMIN',  label: 'แอดมิน'       },
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
      const res = await adminGetCustomerOrders(userId);
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
    } catch (err) {
      toast.error(err?.message ?? 'อัปเดตไม่สำเร็จ');
    } finally { setSaving(false); }
  };

  const handleToggleBan = async () => {
    setSaving(true);
    try {
      const newActive = !selectedUser.isActive;
      await adminSetCustomerActive(selectedUser.id, newActive);
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

  const tabCount = (key) =>
    key === 'ALL'    ? customers.length
    : key === 'google' ? customers.filter(c => c.provider === 'google').length
    : key === 'email'  ? customers.filter(c => c.provider !== 'google').length
    : key === 'USER'   ? customers.filter(c => c.role === 'USER').length
    : key === 'ADMIN'  ? customers.filter(c => c.role === 'ADMIN').length
    : 0;
  const pct = (n) => customers.length ? `${Math.round((n / customers.length) * 100)}%` : '0%';
  const googleCount = tabCount('google');
  const emailCount = tabCount('email');

  const pageItems = Array.from({ length: totalPages }, (_, i) => i + 1)
    .filter(p => p === 1 || p === totalPages || Math.abs(p - page) <= 1)
    .reduce((acc, p, idx, arr) => {
      if (idx > 0 && p - arr[idx - 1] > 1) acc.push('...');
      acc.push(p);
      return acc;
    }, []);

  const ORDER_STATUS_LABEL = { PENDING: 'รอดำเนินการ', CONFIRMED: 'ยืนยันแล้ว', PROCESSING: 'กำลังเตรียม', SHIPPED: 'จัดส่งแล้ว', DELIVERED: 'ได้รับแล้ว', CANCELLED: 'ยกเลิก', REFUNDED: 'คืนเงิน' };

  return (
    <>
      <div className="page-head">
        <div><h1>ลูกค้า</h1><p>รายชื่อลูกค้าทั้งหมดในระบบ</p></div>
        <div className="actions">
          <button className="btn btn-outline" onClick={() => exportCSV(
            customers,
            ['ID', 'ชื่อผู้ใช้', 'Email', 'Login ด้วย', 'Role', 'สมัครเมื่อ', 'Orders'],
            ['id', 'username', 'email', 'provider', 'role', 'createdAt', 'orderCount'],
            'customers'
          )}>
            <Icon name="download" size="sm" />Export
          </button>
        </div>
      </div>

      <div className="stats" style={{ gridTemplateColumns: 'repeat(3,1fr)' }}>
        <div className="stat">
          <div className="k">ลูกค้าทั้งหมด<span style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--blue-soft)', color: 'var(--blue)', display: 'grid', placeItems: 'center' }}><Icon name="users" /></span></div>
          <div className="v">{customers.length}</div>
        </div>
        <div className="stat">
          <div className="k">สมัครด้วยอีเมล<span style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--bg)', color: 'var(--muted)', display: 'grid', placeItems: 'center' }}><Icon name="mail" /></span></div>
          <div className="v">{emailCount}</div><div className="d">{pct(emailCount)}</div>
        </div>
        <div className="stat">
          <div className="k">สมัครด้วย Google<span style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--bg)', color: 'var(--muted)', display: 'grid', placeItems: 'center' }}><Icon name="google" /></span></div>
          <div className="v">{googleCount}</div><div className="d">{pct(googleCount)}</div>
        </div>
      </div>

      <section className="card">
        <div className="toolbar">
          <div className="chips">
            {PROVIDER_TABS.map(t => (
              <button key={t.key} className={`chip${filterTab === t.key ? ' on' : ''}`} onClick={() => setFilterTab(t.key)}>
                {t.label} <span className="n">{tabCount(t.key)}</span>
              </button>
            ))}
          </div>
          <label className="search">
            <Icon name="search" size="sm" />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="ค้นหาชื่อหรืออีเมล" aria-label="ค้นหาลูกค้า" />
          </label>
        </div>

        <div className="table-wrap">
          {loading ? (
            <table className="t"><tbody><SkeletonRows rows={8} cols={6} /></tbody></table>
          ) : (
            <table className="t">
              <thead>
                <tr><th>ลูกค้า</th><th className="hide-m">เข้าสู่ระบบด้วย</th><th className="hide-m">สมัครเมื่อ</th><th className="right">ออเดอร์</th><th className="hide-m">บทบาท</th><th></th></tr>
              </thead>
              <tbody>
                {paginated.length === 0 ? (
                  <tr><td colSpan={6} className="muted" style={{ textAlign: 'center', padding: 48 }}>ไม่พบลูกค้า</td></tr>
                ) : paginated.map(c => (
                  <tr key={c.id}>
                    <td>
                      <div className="prod-cell">
                        <span className="avatar" style={!c.isActive ? { opacity: .5 } : undefined}>{c.username?.charAt(0).toUpperCase()}</span>
                        <div style={{ minWidth: 0 }}>
                          <b style={{ fontWeight: 600, ...(c.isActive ? {} : { color: 'var(--faint)', textDecoration: 'line-through' }) }}>{c.username}</b>
                          {!c.isActive && <span className="badge b-red plain" style={{ marginLeft: 8 }}>ถูกระงับ</span>}
                          <span className="sub">{c.email}</span>
                        </div>
                      </div>
                    </td>
                    <td className="hide-m">
                      <span className="badge plain"><Icon name={c.provider === 'google' ? 'google' : 'mail'} size="sm" />{c.provider === 'google' ? 'Google' : 'Email'}</span>
                    </td>
                    <td className="num hide-m">{fmt(c.createdAt)}</td>
                    <td className="num right" style={{ fontWeight: c.orderCount > 0 ? 600 : 400, color: c.orderCount > 0 ? 'var(--blue)' : 'var(--faint)' }}>{c.orderCount}</td>
                    <td className="hide-m"><span className={`badge plain${c.role === 'ADMIN' ? ' b-violet' : ''}`}>{c.role === 'ADMIN' ? 'แอดมิน' : 'ลูกค้า'}</span></td>
                    <td><div className="row-actions"><button className="btn btn-outline sm" onClick={() => openManage(c)}>จัดการ</button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {!loading && (
          <div className="pager">
            <span>แสดง {filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, filtered.length)} จาก {filtered.length} ลูกค้า</span>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
              <button className="btn btn-outline sm" onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}>ก่อนหน้า</button>
              {pageItems.map((p, i) => p === '...'
                ? <span key={`dot-${i}`} className="faint">…</span>
                : <button key={p} className={`btn sm ${page === p ? 'btn-dark' : 'btn-outline'}`} onClick={() => setPage(p)}
                    aria-current={page === p ? 'page' : undefined}>{p}</button>)}
              <button className="btn btn-outline sm" onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages}>ถัดไป</button>
            </div>
          </div>
        )}
      </section>

      {selectedUser && (
        <div className="modal-scrim" onClick={e => e.target === e.currentTarget && setSelectedUser(null)}>
          <div className="modal" role="dialog" aria-label="จัดการผู้ใช้" style={{ maxWidth: 520 }}>
            <div className="modal-head">
              <h2>จัดการผู้ใช้</h2>
              <button className="icon-btn" onClick={() => setSelectedUser(null)} aria-label="ปิด"><Icon name="x" /></button>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '16px 24px', background: 'var(--bg)' }}>
              <span className="avatar" style={{ width: 48, height: 48, fontSize: 18 }}>{selectedUser.username?.charAt(0).toUpperCase()}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <b style={{ fontWeight: 600, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{selectedUser.username}</b>
                <span className="muted" style={{ fontSize: 13 }}>{selectedUser.email}</span>
              </div>
              {!selectedUser.isActive && <span className="badge b-red">ถูกระงับ</span>}
              <div style={{ textAlign: 'center' }}>
                <b className="head num" style={{ display: 'block', fontSize: 20, color: selectedUser.orderCount > 0 ? 'var(--blue)' : 'var(--faint)' }}>{selectedUser.orderCount}</b>
                <span className="faint" style={{ fontSize: 12 }}>ออเดอร์</span>
              </div>
            </div>

            <div className="tabs" style={{ padding: '0 16px' }} role="tablist">
              {[{ key: 'info', label: 'ข้อมูล & สิทธิ์' }, { key: 'orders', label: 'ประวัติออเดอร์' }].map(t => (
                <button key={t.key} role="tab" aria-selected={modalTab === t.key} className={`tab${modalTab === t.key ? ' on' : ''}`} onClick={() => handleTabChange(t.key)}>
                  {t.label}
                </button>
              ))}
            </div>

            {modalTab === 'info' && (
              <>
                <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div className="field">
                    <label htmlFor="cu-username">ชื่อผู้ใช้</label>
                    <div className="control"><input id="cu-username" value={editUsername} onChange={e => setEditUsername(e.target.value)} /></div>
                  </div>
                  <div className="field">
                    <label>อีเมล <span className="faint" style={{ fontWeight: 400 }}>(แก้ไขไม่ได้)</span></label>
                    <div className="control" style={{ borderStyle: 'dashed', background: 'var(--bg)' }}>{selectedUser.email}</div>
                  </div>
                  <div className="field">
                    <label>บทบาท</label>
                    <div style={{ display: 'flex', gap: 8 }}>
                      {['USER', 'ADMIN'].map(r => (
                        <button key={r} className={`btn ${editRole === r ? 'btn-dark' : 'btn-outline'}`} style={{ flex: 1 }}
                          onClick={() => setEditRole(r)} aria-pressed={editRole === r}>
                          {r === 'ADMIN' ? 'แอดมิน' : 'ลูกค้า'}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, padding: 14, borderRadius: 'var(--r-md)',
                    background: selectedUser.isActive ? 'var(--red-soft)' : 'var(--green-soft)' }}>
                    <div>
                      <b style={{ color: selectedUser.isActive ? 'var(--red)' : 'var(--green)' }}>{selectedUser.isActive ? 'ระงับบัญชี' : 'ปลดระงับบัญชี'}</b>
                      <div className="muted" style={{ fontSize: 13 }}>{selectedUser.isActive ? 'ผู้ใช้จะเข้าสู่ระบบไม่ได้ทันที' : 'ผู้ใช้จะกลับมาเข้าสู่ระบบได้'}</div>
                    </div>
                    <button className={`btn sm ${selectedUser.isActive ? 'btn-danger' : 'btn-primary'}`} onClick={handleToggleBan} disabled={saving}
                      style={!selectedUser.isActive ? { background: 'var(--green)' } : undefined}>
                      {selectedUser.isActive ? 'ระงับ' : 'ปลดระงับ'}
                    </button>
                  </div>
                </div>
                <div className="modal-foot">
                  <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setSelectedUser(null)}>ยกเลิก</button>
                  <button className="btn btn-primary" style={{ flex: 1 }} onClick={handleSaveInfo} disabled={saving}>
                    {saving ? <><Spinner size={16} /> กำลังบันทึก...</> : 'บันทึก'}
                  </button>
                </div>
              </>
            )}

            {modalTab === 'orders' && (
              <div className="modal-body">
                {ordersLoading ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {Array.from({ length: 3 }).map((_, i) => <div key={i} className="sk" style={{ height: 52 }} />)}
                  </div>
                ) : userOrders.length === 0 ? (
                  <p className="muted" style={{ textAlign: 'center', padding: '24px 0', margin: 0 }}>ยังไม่มีออเดอร์</p>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 288, overflowY: 'auto' }}>
                    {userOrders.map(o => (
                      <div key={o.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, border: '1px solid var(--line)', borderRadius: 'var(--r-md)', padding: '10px 14px' }}>
                        <div style={{ minWidth: 0 }}>
                          <span className="mono" style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 12, color: 'var(--blue)', fontWeight: 600 }}>{o.orderNo}</span>
                          <div className="muted" style={{ fontSize: 13 }}>{o.orderLines?.map(l => l.productName).join(', ')}</div>
                        </div>
                        <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                          <b className="num">฿{Number(o.totalAmount).toLocaleString()}</b>
                          <StatusBadge status={o.status} label={ORDER_STATUS_LABEL[o.status] ?? o.status} />
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
