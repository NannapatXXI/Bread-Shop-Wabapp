// app/(app)/home/page.js
'use client'; 

import { useEffect ,useState } from 'react'; // 1. Import useEffect
import { useAuth } from '../../context/AuthContext'; 
import Icon from "../../components/Icon";
import StatusBadge from "../../components/StatusBadge";
import SalesChart from "../../components/SalesChart";
import CategoryDonutChart from "../../components/CategoryDonutChart";
import { getorders } from "@/services/auth.service";
import api from "@/lib/api"; // [Claude] ใช้ดึง summary และ top products
import { useRouter } from "next/navigation";

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';


export default function HomePage() {

  const router = useRouter();
  const [active, setActive] = useState("A");
  const { user } = useAuth();
  const [count, setCount] = useState(0);
  const [orders, setOrders] = useState([]);
  const [summary, setSummary] = useState(null);       // [Claude] ข้อมูล summary cards
  const [topProducts, setTopProducts] = useState([]); // [Claude] สินค้าขายดี
 
  // โหลดออเดอร์ + summary cards + สินค้าขายดี ครั้งเดียวตอนเปิดหน้า
  useEffect(() => {
    getorders()
      .then(res => setOrders(res.data))
      .catch(err => console.error(err));

    Promise.all([
      api.get("/api/v1/admin/dashboard/summary"),
      api.get("/api/v1/admin/dashboard/top-products"),
    ])
      .then(([sumRes, topRes]) => {
        setSummary(sumRes.data);
        setTopProducts(topRes.data);
      })
      .catch(err => console.error("dashboard fetch error", err));
  }, []);

  const getGreeting = () => {
    const hour = new Date().getHours();
  
    if (hour < 11) return "GOOD MORNING";
    if (hour < 16) return "GOOD AFTERNOON";
    if (hour < 19) return "GOOD EVENING";
    return "สวัสดีตอนค่ำ";
  };

 

  const getGraph = () => {
    switch (count) {
      case 0:
        return "ยอดขายรายสัปดาห์";
      case 1:
        return "ยอดขายรายเดือน";
      case 2:
        return "ยอดขายรายปี";
      default:
        return "กรุณาเลือกช่วงเวลา";
    }
  };
  const getInfoGraph = () => {
    switch (count) {
        case 0:
          return "เปรียบเทียบกับสัปดาห์ที่แล้ว";
        case 1:
          return "เปรียบเทียบกับเดือนที่แล้ว";
        case 2:
          return "เปรียบเทียบกับปีที่แล้ว";
        default:
          return "กรุณาเลือกช่วงเวลา";
      }
  };
  const perweek = () => {
    setCount(0);
  };
  const permonth = () => {
    setCount(1);
  };
 
  const peryear= () => {
    setCount(2);
  };


  const STATUS_LABEL = {
    DELIVERED: 'สำเร็จ', PENDING: 'รอดำเนินการ', CONFIRMED: 'ยืนยันแล้ว', PROCESSING: 'กำลังเตรียม',
    SHIPPED: 'จัดส่งแล้ว', CANCELLED: 'ยกเลิก', REFUNDED: 'คืนเงิน',
  };

  const change = (pct) => {
    const v = pct ?? 0;
    return (
      <span style={{ color: v >= 0 ? 'var(--green)' : 'var(--red)', fontWeight: 600 }}>
        {v >= 0 ? '▲' : '▼'} {Math.abs(v).toFixed(1)}%
      </span>
    );
  };

  const statIcon = (name, soft, color) => (
    <span style={{ width: 36, height: 36, borderRadius: 10, background: `var(${soft})`, color: `var(${color})`, display: 'grid', placeItems: 'center' }}>
      <Icon name={name} />
    </span>
  );

  return (
    <>
      <div className="page-head">
        <div>
          <p className="faint" style={{ margin: 0, fontSize: 12, letterSpacing: '.08em' }}>{getGreeting()}</p>
          <h1>ยินดีต้อนรับกลับมา, {user?.username || 'ผู้ใช้'}</h1>
          <p>
            วันนี้มีออเดอร์ใหม่ {summary?.todayOrders ?? '...'} รายการ · ยอดขายวันนี้ ฿{summary ? Number(summary.todayRevenue).toLocaleString('th-TH') : '...'}
          </p>
        </div>
      </div>

      <div className="stats">
        <div className="stat">
          <div className="k">รายได้รวมเดือนนี้{statIcon('money', '--blue-soft', '--blue')}</div>
          <div className="v">฿{summary ? Number(summary.monthRevenue).toLocaleString('th-TH') : '...'}</div>
          <div className="d">{change(summary?.monthRevenueChangePercent)} จากเดือนที่แล้ว</div>
        </div>
        <div className="stat">
          <div className="k">ออเดอร์ทั้งหมด{statIcon('receipt', '--green-soft', '--green')}</div>
          <div className="v">{summary?.totalOrders ?? '...'}</div>
          <div className="d">{change(summary?.totalOrdersChangePercent)} จากเดือนที่แล้ว</div>
        </div>
        <div className="stat">
          <div className="k">ลูกค้าทั้งหมด{statIcon('users', '--amber-soft', '--amber')}</div>
          <div className="v">{summary?.totalCustomers ?? '...'}</div>
          <div className="d">ลูกค้าสะสม</div>
        </div>
        <div className="stat">
          <div className="k">ออเดอร์ยกเลิก{statIcon('x', '--red-soft', '--red')}</div>
          <div className="v">{summary?.cancelledOrders ?? '...'}</div>
          <div className="d">ถูกยกเลิก</div>
        </div>
      </div>

      <div className="two" style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16, marginBottom: 16 }}>
        <section className="card" style={{ minWidth: 0 }}>
          <div className="card-head" style={{ flexWrap: 'wrap' }}>
            <div><h2>{getGraph()}</h2><div className="hint">{getInfoGraph()}</div></div>
            <div className="chips">
              {[['A', 'สัปดาห์', perweek], ['B', 'เดือน', permonth], ['C', 'ปี', peryear]].map(([key, label, fn]) => (
                <button key={key} className={`chip${active === key ? ' on' : ''}`} style={{ height: 32 }}
                  onClick={() => { setActive(key); fn(); }}>{label}</button>
              ))}
            </div>
          </div>
          <div className="pad" style={{ height: 320 }}>
            <SalesChart period={active} />
          </div>
        </section>
        <section className="card" style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <div className="card-head"><div><h2>ยอดขายตามหมวด</h2><div className="hint">สัดส่วนยอดขาย 30 วันล่าสุด</div></div></div>
          <div className="pad" style={{ flex: 1, minHeight: 300 }}>
            <CategoryDonutChart />
          </div>
        </section>
      </div>

      <div className="two" style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16 }}>
        <section className="card" style={{ minWidth: 0 }}>
          <div className="card-head">
            <div><h2>ออเดอร์ล่าสุด</h2><div className="hint">5 ออเดอร์ล่าสุด</div></div>
            <a className="link" href="/admin/orders" style={{ fontSize: 14 }}
              onClick={(e) => { e.preventDefault(); router.push('/admin/orders'); }}>ดูทั้งหมด</a>
          </div>
          <div className="table-wrap">
            <table className="t">
              <thead><tr><th>เลขออเดอร์</th><th className="hide-m">สินค้า</th><th className="right">ราคา</th><th>สถานะ</th></tr></thead>
              <tbody>
                {orders.length === 0 ? (
                  <tr><td colSpan={4} className="muted" style={{ textAlign: 'center', padding: 32 }}>ยังไม่มีออเดอร์</td></tr>
                ) : orders.slice().reverse().slice(0, 5).map((order) => (
                  <tr key={order.id}>
                    <td><span className="mono">{order.orderNo}</span><span className="sub">{order.shippingName}</span></td>
                    <td className="hide-m" style={{ fontSize: 14 }}>{order.orderLines.map(line => line.productName).join(', ')}</td>
                    <td className="right num">฿{order.totalAmount}</td>
                    <td><StatusBadge status={order.status} label={STATUS_LABEL[order.status] ?? 'ไม่พบค่า'} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="card" style={{ minWidth: 0 }}>
          <div className="card-head"><div><h2>สินค้าขายดี</h2><div className="hint">30 วันล่าสุด</div></div></div>
          <div className="pad" style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: 14 }}>
            {topProducts.length === 0 && <p className="muted" style={{ margin: 0, textAlign: 'center' }}>ยังไม่มีข้อมูล</p>}
            {topProducts.map((item, index) => {
              const maxQty = topProducts[0]?.totalQty || 1;
              return (
                <div key={item.productId} style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  <span className="faint num" style={{ width: 14 }}>{index + 1}</span>
                  {item.imageUrl
                    ? <img className="thumb" src={`${API_URL}/${item.imageUrl}`} alt={item.productName} />
                    : <span className="thumb" />}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <b style={{ fontWeight: 600 }}>{item.productName}</b>
                    <div className="muted" style={{ fontSize: 13 }}>ขายแล้ว {item.totalQty} ชิ้น</div>
                    <div className="bars"><div className="track" style={{ height: 4, marginTop: 4 }}>
                      <div className="fill" style={{ width: `${(item.totalQty / maxQty) * 100}%` }} />
                    </div></div>
                  </div>
                  <b className="num">฿{Number(item.totalRevenue).toLocaleString('th-TH')}</b>
                </div>
              );
            })}
          </div>
        </section>
      </div>
      <style>{`@media(max-width:1100px){.two{grid-template-columns:1fr!important}}`}</style>
    </>
  );
}
