'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '../context/AuthContext';
import { NotificationProvider } from '../context/NotificationContext';
import Brand from '../components/Brand';
import Icon from '../components/Icon';
import NotificationBell from '../components/NotificationBell';

const NAV = [
  ['/admin/dashbord',   'grid',    'ภาพรวม',    'ภาพรวม'],
  ['/admin/orders',     'receipt', 'ออเดอร์',    'ออเดอร์'],
  ['/admin/products',   'box',     'สินค้า',     'สินค้า'],
  ['/admin/promotions', 'tag',     'โปรโมชั่น',  'โปรฯ'],
  ['/admin/storefront', 'store',   'หน้าร้าน',   'หน้าร้าน'],
  ['/admin/customers',  'users',   'ลูกค้า',     'ลูกค้า'],
  ['/admin/logs',       'shield',  'Logs',      'Logs'],
];

const SUB_CRUMB = [
  ['/admin/products/addproduct', 'เพิ่มสินค้า'],
  ['/admin/products/editproduct', 'แก้ไขสินค้า'],
];

export default function AdminLayout({ children }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const current = NAV.find(([href]) => pathname.startsWith(href));
  const sub = SUB_CRUMB.find(([href]) => pathname.startsWith(href));

  return (
    <NotificationProvider>
      <div className="admin">
        <aside className="side">
          <Brand href="/admin/dashbord" />
          <div className="grp">จัดการร้าน</div>
          {NAV.map(([href, icon, label]) => {
            const on = current?.[0] === href;
            return (
              <Link key={href} href={href} className={`nav${on ? ' on' : ''}`} aria-current={on ? 'page' : undefined} title={label}>
                <Icon name={icon} /><span>{label}</span>
              </Link>
            );
          })}
          <div className="grp">หน้าร้าน</div>
          <Link className="nav" href="/home" title="ไปหน้าร้าน"><Icon name="store" /><span>ไปหน้าร้าน</span></Link>
          <div className="foot">
            <button className="nav" onClick={logout} title="ออกจากระบบ"
              style={{ width: '100%', background: 'none', border: 0, color: 'inherit', textAlign: 'left' }}>
              <Icon name="logout" /><span>ออกจากระบบ</span>
            </button>
          </div>
        </aside>

        <div className="admin-main">
          <div className="admin-top">
            <span className="m-brand"><Brand href="/admin/dashbord" /></span>
            <div className="crumb">
              {sub
                ? <><Link href={current?.[0] ?? '/admin/products'}>{current?.[2]}</Link> / <b>{sub[1]}</b></>
                : <b>{current?.[2] ?? 'หลังร้าน'}</b>}
            </div>
            <div className="tools">
              <NotificationBell />
              <Link className="avatar" href="/profile" aria-label={`โปรไฟล์ ${user?.username ?? ''}`}>
                {user?.username?.charAt(0).toUpperCase() ?? ''}
              </Link>
            </div>
          </div>
          <div className="admin-body">
            <main>{children}</main>
          </div>
        </div>
      </div>

      <nav className="bottom-nav admin-bn" aria-label="เมนูแอดมิน (มือถือ)">
        {NAV.map(([href, icon, , short]) => {
          const on = current?.[0] === href;
          return (
            <Link key={href} href={href} className={on ? 'on' : undefined} aria-current={on ? 'page' : undefined}>
              <Icon name={icon} /><span>{short}</span>
            </Link>
          );
        })}
      </nav>
    </NotificationProvider>
  );
}
