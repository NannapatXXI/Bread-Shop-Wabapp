// app/(app)/home/page.js
'use client'; 

import { useEffect ,useState } from 'react'; // 1. Import useEffect
import { useCart } from '../../CartContext';
import { useAuth } from '../../context/AuthContext'; 
import { getMe } from "@/services/auth.service";
import { MdOutlineAttachMoney } from "react-icons/md";
import { BiChevronDownSquare,BiGroup,BiMessageSquareX } from "react-icons/bi";
import SalesChart from "../../components/SalesChart";
import CategoryDonutChart from "../../components/CategoryDonutChart";
import { getorders } from "@/services/auth.service";
import api from "@/lib/api"; // [Claude] ใช้ดึง summary และ top products
import { useRouter } from "next/navigation";


export default function HomePage() {

  const router = useRouter();
  const [active, setActive] = useState("A");
  const { addToCart } = useCart();
  const { user, loading } = useAuth();
  const [mail, setMail] = useState("");
  const [count, setCount] = useState(0);
  const [categoryFilter, setcategoryFilter] = useState("");
  const [orders, setOrders] = useState([]);
  const [summary, setSummary] = useState(null);       // [Claude] ข้อมูล summary cards
  const [topProducts, setTopProducts] = useState([]); // [Claude] สินค้าขายดี
  useEffect(() => {
  
    const token = localStorage.getItem('token');
    
    
    console.log("=========== DEBUG LOGIN ===========");
    console.log("Token:", token);      
    console.log("User:", user);      
    console.log("Loading:", loading);  
    console.log("===================================");
  }, [user, loading]); // ให้ทำงานใหม่เมื่อ user หรือ loading เปลี่ยนแปลง

 
  useEffect(() => {
    const savedMail = localStorage.getItem("test_mail");
    if (savedMail) {
      setMail(savedMail);
    }
    console.log("User ตอน render:", user);
  }, [user]); // ให้ทำงานใหม่เมื่อ user เปลี่ยนแปลง

  // บันทึกลง localStorage ทุกครั้งที่ mail เปลี่ยน
  useEffect(() => {
    fetchOrders();
    fetchDashboard(); // [Claude] ดึง summary + top products พร้อมกัน
    localStorage.setItem("test_mail", mail);
  }, [mail]);



  
  const fetchOrders = async () => {
    try {
      const res = await getorders();
      setOrders(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  // [Claude] ดึง summary cards และ top products จาก dashboard endpoints
  const fetchDashboard = async () => {
    try {
      const [sumRes, topRes] = await Promise.all([
        api.get("/api/v1/admin/dashboard/summary"),
        api.get("/api/v1/admin/dashboard/top-products"),
      ]);
      setSummary(sumRes.data);
      setTopProducts(topRes.data);
    } catch (err) {
      console.error("dashboard fetch error", err);
    }
  };

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


  const renderStatus = (status) => {
   
    if (status === 'DELIVERED')  return <div className="px-3 py-1 bg-green-100 text-green-700 rounded-md">สำเร็จ</div>
    if (status === 'PENDING')    return <div className="px-3 py-1 bg-yellow-100 text-yellow-700 rounded-md">รอดำเนินการ</div>
    if (status === 'CONFIRMED')  return <div className="px-3 py-1 bg-blue-100 text-blue-700 rounded-md">ยืนยันแล้ว</div>
    if (status === 'PROCESSING') return <div className="px-3 py-1 bg-purple-100 text-purple-700 rounded-md">กำลังเตรียม</div>
    if (status === 'SHIPPED')    return <div className="px-3 py-1 bg-indigo-100 text-indigo-700 rounded-md">จัดส่งแล้ว</div>
    if (status === 'CANCELLED')  return <div className="px-3 py-1 bg-orange-100 text-orange-700 rounded-md">ยกเลิก</div>
    if (status === 'REFUNDED')   return <div className="px-3 py-1 bg-red-100 text-red-700 rounded-md">คืนเงิน</div>
  };

  

  return (
    <div className='w-full min-h-screen bg-[#EEF4FB]'>
        <div className='w-full h-36 grid grid-cols-2 gap-2  bg-[#0B1F33] bg-[radial-gradient(circle_at_80%_50%,rgba(58,123,213,0.35),transparent_40%)]  rounded-2xl'>   
            <div className=' text-white w-full h-36 '>

                <div className='text-[#8ba6ca] text-2xl font-bold pt-8 pl-10'>
                        <p className='text-sm pb-2'>{getGreeting()}</p>
                        <h1 className='text-white'>ยินดีต้อนรับกลับมา, 
                        <span className="text-[#A8CEFF] font-semibold pb-2">
                            {user?.username || "ผู้ใช้"}
                        </span>
                        </h1>
                        <p className='text-sm text-gray-500'>วันนี้มีออเดอร์ใหม่ {summary?.todayOrders ?? '...'} รายการ</p>


                </div>
                    
            </div>
            <div className=' text-white w-full h-36 '>

                <div className='grid grid-cols-[150px_10px_150px_10px_150px] w-1000 gap-2  items-center  justify-end h-full  '>
                    <div>
                        <p className='text-2xl font-bold'>฿{summary ? Number(summary.todayRevenue).toLocaleString('th-TH') : '...'}</p>
                        <h1 className='text-sm text-gray-400'>ยอดขายวันนี้</h1>
                    </div>

                    <div className=' text-2xl text-gray-400 flex items-center justify-center'>
                          |
                    </div>

                    <div>
                        <p className='text-2xl font-bold'>{summary?.todayOrders ?? '...'}</p>
                        <h1 className='text-sm text-gray-400'>คำสั่งซื้อวันนี้</h1>
                    </div>

                    <div className=' text-2xl text-gray-400 flex items-center justify-center'>
                          |
                        </div>
                    <div>
                        <p className='text-2xl font-bold'>{summary?.totalCustomers ?? '...'}</p>
                        <h1 className='text-sm text-gray-400'>ลูกค้าทั้งหมด</h1>
                    </div>
                </div>
                    
            </div>
        </div>


        <div className="w-full grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-6 mt-6">

                {/* CARD 1 */}
                <div className="bg-white rounded-2xl shadow-md border-b-2 border-blue-500 hover:shadow-xl transition">
                <div className="flex justify-between items-start p-5">

                    <div>
                    <p className="text-gray-400 text-sm mb-4">
                        รายได้รวมเดือนนี้
                    </p>

                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 mb-3">
                        ฿{summary ? Number(summary.monthRevenue).toLocaleString('th-TH') : '...'}
                    </h1>

                    <div className="flex items-center gap-2">
                        <span className={`font-semibold ${(summary?.monthRevenueChangePercent ?? 0) >= 0 ? 'text-green-600' : 'text-red-500'}`}>
                          {(summary?.monthRevenueChangePercent ?? 0) >= 0 ? '▲' : '▼'} {Math.abs(summary?.monthRevenueChangePercent ?? 0).toFixed(1)}%
                        </span>
                        <span className="text-gray-400 text-sm">vs เดือนที่แล้ว</span>
                    </div>
                    </div>

                    <div className="bg-blue-100 p-4 rounded-xl">
                    <MdOutlineAttachMoney size={28} className="text-blue-600" />
                    </div>

                </div>
                </div>

                {/* CARD 2 */}
                <div className="bg-white rounded-2xl shadow-md border-b-2 border-green-500 hover:shadow-xl transition">
                <div className="flex justify-between items-start p-5">

                    <div>
                    <p className="text-gray-400 text-sm mb-4">
                        ออเดอร์ทั้งหมด
                    </p>

                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 mb-3">
                        {summary?.totalOrders ?? '...'}
                    </h1>

                    <div className="flex items-center gap-2">
                        <span className={`font-semibold ${(summary?.totalOrdersChangePercent ?? 0) >= 0 ? 'text-green-600' : 'text-red-500'}`}>
                          {(summary?.totalOrdersChangePercent ?? 0) >= 0 ? '▲' : '▼'} {Math.abs(summary?.totalOrdersChangePercent ?? 0).toFixed(1)}%
                        </span>
                        <span className="text-gray-400 text-sm">vs เดือนที่แล้ว</span>
                    </div>
                    </div>

                    <div className="bg-green-100 p-4 rounded-xl">
                    <BiChevronDownSquare size={28} className="text-green-600" />
                    </div>

                </div>
                </div>

                {/* CARD 3 */}
                <div className="bg-white rounded-2xl shadow-md border-b-2 border-orange-500 hover:shadow-xl transition">
                <div className="flex justify-between items-start p-5">

                    <div>
                    <p className="text-gray-400 text-sm mb-4">
                        ลูกค้าทั้งหมด
                    </p>

                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 mb-3">
                        {summary?.totalCustomers ?? '...'}
                    </h1>

                    <div className="flex items-center gap-2">
                        <span className="text-green-600 font-semibold">—</span>
                        <span className="text-gray-400 text-sm">ลูกค้าสะสม</span>
                    </div>
                    </div>

                    <div className="bg-orange-100 p-4 rounded-xl">
                    <BiGroup size={28} className="text-orange-600" />
                    </div>

                </div>
                </div>

                {/* CARD 4 */}
                <div className="bg-white rounded-2xl shadow-md border-b-2 border-red-500 hover:shadow-xl transition">
                <div className="flex justify-between items-start p-5">

                    <div>
                    <p className="text-gray-400 text-sm mb-4">
                        ออเดอร์ยกเลิก
                    </p>

                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 mb-3">
                        {summary?.cancelledOrders ?? '...'}
                    </h1>

                    <div className="flex items-center gap-2">
                        <span className="text-gray-400 text-sm">ออเดอร์ที่ถูกยกเลิก</span>
                    </div>
                    </div>

                    <div className="bg-red-100 p-4 rounded-xl">
                    <BiMessageSquareX size={28} className="text-red-600" />
                    </div>

                </div>
                </div>

        </div>
        <div className="grid grid-cols-3 gap-4  mt-6">
            <div className=' col-span-2  h-96 rounded-2xl bg-white  shadow-md ' >
                    <div className=" m-2 h-full flex flex-col">

                        <div className=' h-16 w-full flex  items-center   text-gray-500 font-semibold'> 

                                <div className=' w-1/2 pl-6'>
                                        <h1 className='text-black text-2xl'>{getGraph()}</h1>
                                        <p className='text-sm'>{getInfoGraph()}</p>
                                </div>
                                <div className=' w-1/2 flex items-center justify-end pr-4'>
                                       
                                       <div className = 'flex gap-2 items-center justify-end pr-4 '>
                                               <button  onClick={() => {
                                                            setActive("A");
                                                            perweek();
                                                        }}
                                                        className={`px-4 py-2 rounded-md font-semibold transition-all ${
                                                            active === "A"
                                                            ? "bg-[#0F2235] text-[#A8CEFF]"
                                                            : "hover:bg-gray-200 text-gray-400"
                                                        }`}
                                                        >
                                                        สัปดาห์
                                                </button>
                                                <button onClick={() => {
                                                        setActive("B");
                                                        permonth();
                                                    }}
                                                    className={`px-4 py-2 rounded-md font-semibold transition-all ${
                                                        active === "B"
                                                        ? "bg-[#0F2235] text-[#A8CEFF]"
                                                        : "hover:bg-gray-200 text-gray-400"
                                                    }`}
                                                    >
                                                    เดือน
                                                </button>
                                                 <button onClick={() => {
                                                        setActive("C");
                                                        peryear();
                                                    }}
                                                    className={`px-4 py-2 rounded-md font-semibold transition-all ${
                                                        active === "C"
                                                        ? "bg-[#0F2235] text-[#A8CEFF]"
                                                        : "hover:bg-gray-200 text-gray-400"
                                                    }`}
                                                    >
                                                    ปี
                                                </button>
                                       </div>
                                </div>
                    
                         </div>
                         <div className="flex-1 p-4 ">
                             <SalesChart period={active} />
                         </div>
                    </div>
            </div>
           
           
                <div className='  h-96 rounded-2xl bg-white  shadow-md font-semibold' >
                        <div className=" m-2 ">
                            <div className='pl-6 pt-2 '>
                                <h1 className='text-black text-2xl'>หมวดหมู่สินค้า</h1>
                               <p className='text-gray-600 text-sm'>สัดส่วนยอดขาย 30 วันล่าสุด</p>
                            </div>
                            <div className="flex-1 p-4 rounded-2xl  pr-5">
                                <CategoryDonutChart />
                            </div>
                        </div>
                </div>
           
        </div>
        <div className="grid grid-cols-4 gap-4 h-[500px] w-full mt-6 rounded-2xl   ">
            <div className="col-span-3 h-full flex flex-col rounded-2xl bg-white shadow-md font-semibold w-full ">
                    
                    <div className="h-28 w-full shrink-0 flex items-center pt-2 text-gray-500 font-semibold  ">

                        <div className="w-1/2 pl-6">
                                <h1 className='text-black text-2xl'>ออเดอร์ล่าสุด</h1>
                                <p className='text-sm'> 5 ออเดอร์ล่าสุด</p>
                        </div>
                        <div className=' w-1/2 flex items-center justify-end pr-4'>
                            
                            <div   onClick={() => router.push("/admin/orders")} className = 'flex gap-2 items-center justify-end pr-4 text-blue-500 hover:underline cursor-pointer'>
                                    <p>ดูทั้งหมด ⭢ </p>
                            </div>
                        </div>
                            
                  
                    </div>
                    <div className="flex-1 min-h-0 overflow-auto px-4 pt-2    pb-4 ">
                        <div className="overflow-hidden rounded-xl border border-gray-300 bg-white">
                                        <table className="min-w-full table-fixed border-separate border-spacing-0   ">
                                            <thead className="text-white">
                                            <tr className="h-16">
                                                <th className="rounded-tl-xl bg-[#0F2235] px-4 py-4 text-center text-sm font-semibold align-middle">
                                                id
                                                </th>
                                                <th className="bg-[#0F2235] px-6 py-4 text-center text-sm font-semibold align-middle">
                                                Customer name
                                                </th>
                                                <th className="bg-[#0F2235] px-6 py-4 text-center text-sm font-semibold align-middle">
                                                Product name
                                                </th>
                                                <th className="bg-[#0F2235] px-6 py-4 text-center text-sm font-semibold align-middle">
                                                Price
                                                </th>
                                                <th className="rounded-tr-xl bg-[#0F2235] px-6 py-4 text-center text-sm font-semibold align-middle">
                                                Status
                                                </th>
                                            </tr>
                                            </thead>
                
                                            <tbody className="divide-y divide-gray-200">
                
                                            {orders.length === 0 ? (
                                            <tr className="h-20">
                                                <td colSpan={5} className="text-gray-400 text-center align-middle">
                                                ไม่มีข้อมูลสินค้า
                                                </td>
                                            </tr>
                                            ) : orders.slice().reverse().slice(0, 5).map((order) => (
                                              
                                            <tr key={order.id} className="h-16 text-center hover:bg-gray-50">
                                                <td className="text-[#8ba6ca] px-4 align-middle">{order.orderNo}</td>
                                                <td className="px-4 align-middle">{order.shippingName}</td>
                                                <td className="px-4 text-sm align-middle">
                                                    {order.orderLines.map(line => line.productName).join(', ')}
                                                </td>
                                                <td className="px-4 align-middle">{order.totalAmount}</td>
                                                <td className="px-4 align-middle">
                                                    {renderStatus(order.status) || (
                                                        <span className="inline-block px-3 py-1 bg-gray-100 rounded-md text-sm">ไม่พบค่า</span>
                                                    )}
                                                </td>
                                            </tr>
                                            ))}
                                            </tbody>
                                        </table>
                        </div>
                    </div>
            </div>
            <div className='  rounded-2xl bg-white  shadow-md   font-semibold   ' >  
                 <div className='p-6 '>
                                <h1 className='text-black text-2xl font-bold'>สินค้าขายดี</h1>
                                <p className='text-gray-600 text-sm mb-4'>30 วันล่าสุด</p>

                                {/* [Claude] แสดง top products จาก backend แทน hardcode */}
                                {topProducts.length === 0 && (
                                  <p className='text-gray-400 text-sm text-center py-4'>ยังไม่มีข้อมูล</p>
                                )}
                                {topProducts.map((item, index) => {
                                  const maxQty = topProducts[0]?.totalQty || 1;
                                  return (
                                    <div key={item.productId} className='flex items-center gap-3 mb-3'>
                                      <span className='text-gray-400 text-sm w-4'>{index + 1}</span>
                                      {item.imageUrl
                                        ? <img src={`http://localhost:8080/${item.imageUrl}`} alt={item.productName} className='w-10 h-10 rounded-md object-cover border flex-shrink-0' />
                                        : <div className='w-10 h-10 rounded-md bg-gray-100 border flex-shrink-0 flex items-center justify-center text-gray-300 text-xs'>No img</div>
                                      }
                                      <div className='flex-1'>
                                        <p className='text-sm font-semibold'>{item.productName}</p>
                                        <p className='text-xs text-gray-400'>ขายแล้ว {item.totalQty} ชิ้น</p>
                                        <div className='w-full bg-gray-200 rounded-full h-1 mt-1'>
                                          <div
                                            className='bg-blue-500 h-1 rounded-full'
                                            style={{ width: `${(item.totalQty / maxQty) * 100}%` }}
                                          />
                                        </div>
                                      </div>
                                      <span className='text-sm font-bold text-gray-700'>
                                        ฿{Number(item.totalRevenue).toLocaleString('th-TH')}
                                      </span>
                                    </div>
                                  );
                                })}
                </div>
            </div>
                            
        </div>
        <div className='  rounded-2xlbg-[#EEF4FB]    h-[200]  ' >  
                  
        </div>
            
    </div>
  );
}