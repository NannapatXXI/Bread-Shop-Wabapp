// components/Sidebar.js

import Link from 'next/link';
import Image from 'next/image';
import { useAuth } from "../context/AuthContext";
// 1. Import ไอคอน Home, Cog, SignOut, และ "Times" (ปุ่ม X)
import { FaHome, FaSignOutAlt, FaTimes, FaUsers, FaTags, FaShieldAlt } from 'react-icons/fa';
import { GrHistory } from "react-icons/gr";
import { MdPerson, MdDashboard, MdOutlineManageSearch, MdReceiptLong } from "react-icons/md";
import { VscArchive } from "react-icons/vsc";


// 2. รับ props 'isOpen' และ 'setIsOpen'
export default function Sidebar({ isOpen, setIsOpen }) {
  const { user, loading,logout } = useAuth();
  console.log("Sidebar User:", user);
  return (
    // 3. เพิ่ม/แก้ไข ClassName ให้ Responsive
    <div 
      className={`
        w-64 h-screen bg-[#0F2235] text-white flex flex-col
        fixed inset-y-0 left-0 z-30 
        bg-[radial-gradient(ellipse_at_bottom_left,_rgba(58,123,213,0.2),_transparent)]
        transition-transform duration-300 ease-in-out
        ${isOpen ? 'translate-x-0' : '-translate-x-full'}
        md:relative md:translate-x-0
      `}
    >
      {/* 4. เพิ่ม "ปุ่ม X" สำหรับปิดในจอมือถือ */}
      <div className="flex justify-between items-center p-4">
        <div className="flex items-center gap-2">
          <Image
            src="/logo.png"
            alt="Peak Pung Logo"
            width={50}
            height={50}
            className="rounded-full object-cover"
          />
          <div className="flex flex-col justify-center mt-3">
            <p className="text-white font-bold text-sm leading-tight m-0">Peak Pung</p>
            <p className="text-gray-400 text-xs leading-tight m-0">by Mom Hmee</p>
          </div>
        </div>
        <button
          onClick={() => setIsOpen(false)}
          className="md:hidden text-gray-400 hover:text-white"
        >
          <FaTimes size={20} />
        </button>
      </div>

      {/* (ส่วน Links และ Logout เหมือนเดิม) */}
      <nav className="flex-1 px-4 py-2 space-y-2">
        <Link href="/home" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
          <FaHome className="mr-3" />
          Home
        </Link>

        <Link href="/product" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
            <VscArchive className="mr-3" />
          Product
        </Link>

        {/* [Claude] แก้ path จาก /home/product → /history */}
        <Link href="/history" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
            <GrHistory className="mr-3" />
          History
        </Link>

        <Link href="/profile" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
            <MdPerson className="mr-3" />
          Profile
        </Link>
        {/* ADMIN เห็น */}
          {user?.roles?.some(r => r.authority === "ROLE_ADMIN")&& (
            <>  
              <div className="border-t border-gray-700 my-2">
                  <div>
                    <h1 className="text-xs font-semibold text-gray-400 uppercase px-2 pt-2">
                      Admin Panel
                    </h1>
                  </div>
              </div> 
              <Link href="/admin/dashbord" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
                <MdDashboard className="mr-3" />
                Dashboard
              </Link>
              <Link href="/admin/products" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
                <MdOutlineManageSearch className="mr-3" />
                Products
              </Link>
              <Link href="/admin/orders" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
                <MdReceiptLong className="mr-3" />
                Orders
              </Link>
              <Link href="/admin/promotions" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
                <FaTags className="mr-3" />
                Promotions
              </Link>
              <Link href="/admin/customers" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
                <FaUsers className="mr-3" />
                Customers
              </Link>
              <Link href="/admin/logs" className="flex items-center p-2 rounded-lg hover:bg-gray-700">
                <FaShieldAlt className="mr-3" />
                Logs
              </Link>
            </>
          )}

      </nav>

      <div className="p-4 border-t border-gray-700">
        <button onClick={logout} className="flex items-center p-2 rounded-lg hover:bg-gray-700 w-full">
          <FaSignOutAlt className="mr-3" />
          Logout
        </button>
    </div>
    </div>
  );
}