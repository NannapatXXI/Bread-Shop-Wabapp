'use client';

// [Claude] หน้า Profile — แสดง/แก้ไขข้อมูลส่วนตัว และจัดการที่อยู่จัดส่ง
// รองรับการเลือกที่อยู่จากแผนที่ผ่าน MapPicker (Leaflet + OpenStreetMap)

import { useEffect, useState, useRef } from 'react';
import dynamic from 'next/dynamic';
import { useAuth } from '../../context/AuthContext';
import api from '@/lib/api';
import { sendOTPEmail, verifyOTP, resetPassword } from '@/services/auth.service';

// [Claude] dynamic import เพราะ Leaflet ใช้ window ซึ่งไม่มีใน SSR
const MapPicker = dynamic(() => import('../../components/MapPicker'), { ssr: false });

export default function ProfilePage() {
  const { user, loading } = useAuth();

  // ─── ข้อมูลส่วนตัว ────────────────────────────────────────────
  const [editingProfile, setEditingProfile] = useState(false);
  const [username, setUsername]             = useState('');
  const [profileMsg, setProfileMsg]         = useState('');

  // ─── เปลี่ยนรหัสผ่าน ─────────────────────────────────────────
  // step: 'idle' | 'otp' | 'newpass'
  const [pwStep,      setPwStep]      = useState('idle');
  const [otpToken,    setOtpToken]    = useState('');
  const [otpValue,    setOtpValue]    = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPw,   setConfirmPw]   = useState('');
  const [pwLoading,   setPwLoading]   = useState(false);
  const [pwMsg,       setPwMsg]       = useState({ text: '', ok: true });
  const [countdown,   setCountdown]   = useState(0); // วินาที cooldown ส่งซ้ำ
  const countdownRef = useRef(null);

  // ─── ที่อยู่ ──────────────────────────────────────────────────
  const [addresses, setAddresses]           = useState([]);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState(null);
  const [addressMsg, setAddressMsg]         = useState('');
  const [showMap, setShowMap]               = useState(false); // [Claude] เปิด/ปิด modal แผนที่

  // ฟอร์มที่อยู่ (ใช้ทั้ง เพิ่ม และ แก้ไข)
  const emptyForm = {
    name: '', recipientName: '', phone: '',
    address: '', province: '', district: '',
    subdistrict: '', postcode: '', isDefault: false,
  };
  const [form, setForm] = useState(emptyForm);

  // ─── โหลดข้อมูลเริ่มต้น ──────────────────────────────────────
  useEffect(() => {
    if (!user) return;
    setUsername(user.username || '');
    fetchAddresses();
  }, [user]);

  // [Claude] ดึงรายการที่อยู่ของ user จาก backend
  const fetchAddresses = async () => {
    try {
      const res = await api.get(`/api/users/${user.id}/addresses`);
      setAddresses(res.data);
    } catch {
      setAddressMsg('โหลดที่อยู่ไม่สำเร็จ');
    }
  };

  // ─── เปลี่ยนรหัสผ่าน helpers ─────────────────────────────────
  const startCountdown = (sec = 60) => {
    setCountdown(sec);
    clearInterval(countdownRef.current);
    countdownRef.current = setInterval(() => {
      setCountdown(v => {
        if (v <= 1) { clearInterval(countdownRef.current); return 0; }
        return v - 1;
      });
    }, 1000);
  };

  // ขั้น 1: ส่ง OTP ไปที่ email ของ user ที่ login อยู่
  const handleSendOtp = async () => {
    setPwLoading(true);
    setPwMsg({ text: '', ok: true });
    try {
      const res = await sendOTPEmail(user.email);
      setOtpToken(res.data.token);
      setPwStep('otp');
      startCountdown(60);
      setPwMsg({ text: `ส่ง OTP ไปที่ ${user.email} แล้ว`, ok: true });
    } catch {
      setPwMsg({ text: 'ส่ง OTP ไม่สำเร็จ กรุณาลองใหม่', ok: false });
    } finally { setPwLoading(false); }
  };

  // ขั้น 2: ตรวจ OTP
  const handleVerifyOtp = async () => {
    if (!otpValue.trim()) return;
    setPwLoading(true);
    setPwMsg({ text: '', ok: true });
    try {
      await verifyOTP({ token: otpToken, otp: otpValue });
      setPwStep('newpass');
      setPwMsg({ text: 'OTP ถูกต้อง กรุณาตั้งรหัสผ่านใหม่', ok: true });
    } catch {
      setPwMsg({ text: 'OTP ไม่ถูกต้องหรือหมดอายุ', ok: false });
    } finally { setPwLoading(false); }
  };

  // ขั้น 3: บันทึกรหัสผ่านใหม่
  const handleResetPassword = async () => {
    if (newPassword.length < 8) {
      setPwMsg({ text: 'รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร', ok: false }); return;
    }
    if (newPassword !== confirmPw) {
      setPwMsg({ text: 'รหัสผ่านไม่ตรงกัน', ok: false }); return;
    }
    setPwLoading(true);
    try {
      await resetPassword({ token: otpToken, newPassword });
      setPwMsg({ text: 'เปลี่ยนรหัสผ่านสำเร็จ', ok: true });
      // reset กลับ idle
      setPwStep('idle');
      setOtpValue(''); setNewPassword(''); setConfirmPw(''); setOtpToken('');
    } catch {
      setPwMsg({ text: 'เปลี่ยนรหัสผ่านไม่สำเร็จ กรุณาลองใหม่', ok: false });
    } finally { setPwLoading(false); }
    setTimeout(() => setPwMsg({ text: '', ok: true }), 4000);
  };

  const cancelPwChange = () => {
    setPwStep('idle'); setOtpValue(''); setNewPassword('');
    setConfirmPw(''); setOtpToken(''); setPwMsg({ text: '', ok: true });
    clearInterval(countdownRef.current);
  };

  // ─── แก้ไขข้อมูลส่วนตัว ──────────────────────────────────────
  const handleSaveProfile = async () => {
    try {
      await api.put('/api/v1/auth/me', { username });
      setProfileMsg('บันทึกสำเร็จ');
      setEditingProfile(false);
    } catch {
      setProfileMsg('บันทึกไม่สำเร็จ กรุณาลองใหม่');
    }
    setTimeout(() => setProfileMsg(''), 3000);
  };

  // ─── จัดการฟอร์มที่อยู่ ──────────────────────────────────────
  const handleFormChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  };

  // [Claude] เปิดฟอร์มเพิ่มที่อยู่ใหม่
  const openAddForm = () => {
    setForm(emptyForm);
    setEditingAddressId(null);
    setShowAddressForm(true);
  };

  // [Claude] เปิดฟอร์มแก้ไขที่อยู่ที่มีอยู่แล้ว
  const openEditForm = (addr) => {
    setForm({
      name:          addr.name          || '',
      recipientName: addr.recipientName || '',
      phone:         addr.phone         || '',
      address:       addr.address       || '',
      province:      addr.province      || '',
      district:      addr.district      || '',
      subdistrict:   addr.subdistrict   || '',
      postcode:      addr.postcode      || '',
      isDefault:     addr.isDefault     || false,
    });
    setEditingAddressId(addr.id);
    setShowAddressForm(true);
  };

  // [Claude] รับค่าที่อยู่จาก MapPicker แล้วกรอกลงฟอร์ม
  // spread โดยตรง ไม่ใช้ || fallback เพื่อให้ค่าที่ map ดึงมา override ฟอร์มเสมอ
  // (name/recipientName/phone ไม่ถูกแตะเพราะ addressData ไม่มี key พวกนั้น)
  const handleMapConfirm = (addressData) => {
    setForm(prev => ({ ...prev, ...addressData }));
    setShowMap(false);
  };

  // [Claude] บันทึกที่อยู่ — ถ้ามี editingAddressId คือแก้ไข, ไม่มีคือเพิ่มใหม่
  const handleSaveAddress = async () => {
    try {
      if (editingAddressId) {
        await api.put(`/api/users/${user.id}/addresses/${editingAddressId}`, form);
      } else {
        await api.post(`/api/users/${user.id}/addresses`, form);
      }
      setAddressMsg('บันทึกที่อยู่สำเร็จ');
      setShowAddressForm(false);
      setEditingAddressId(null);
      fetchAddresses();
    } catch {
      setAddressMsg('บันทึกที่อยู่ไม่สำเร็จ');
    }
    setTimeout(() => setAddressMsg(''), 3000);
  };

  // [Claude] ลบที่อยู่
  const handleDeleteAddress = async (addressId) => {
    if (!confirm('ต้องการลบที่อยู่นี้?')) return;
    try {
      await api.delete(`/api/users/${user.id}/addresses/${addressId}`);
      setAddressMsg('ลบที่อยู่สำเร็จ');
      fetchAddresses();
    } catch {
      setAddressMsg('ลบไม่สำเร็จ');
    }
    setTimeout(() => setAddressMsg(''), 3000);
  };

  // [Claude] ตั้ง default ที่อยู่
  const handleSetDefault = async (addressId) => {
    try {
      await api.put(`/api/users/${user.id}/addresses/${addressId}/default`);
      fetchAddresses();
    } catch {
      setAddressMsg('ตั้งค่าไม่สำเร็จ');
      setTimeout(() => setAddressMsg(''), 3000);
    }
  };

  if (loading) return <div className="text-center py-10 text-gray-400">กำลังโหลด...</div>;

  return (
    <div className="max-w-2xl mx-auto flex flex-col gap-6">

      {/* ─── ส่วนที่ 1: ข้อมูลส่วนตัว + เปลี่ยนรหัสผ่าน ─── */}
      <section className="bg-white rounded-2xl shadow-sm p-7">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-gray-800">ข้อมูลส่วนตัว</h2>
          {!editingProfile && pwStep === 'idle' && (
            <button
              onClick={() => setEditingProfile(true)}
              className="text-sm bg-gray-100 hover:bg-gray-200 text-gray-700 px-4 py-1.5 rounded-lg transition-colors"
            >
              แก้ไข
            </button>
          )}
        </div>

        {/* ชื่อผู้ใช้ */}
        <div className="mb-4">
          <p className="text-xs text-gray-500 mb-1 font-medium">ชื่อผู้ใช้</p>
          {editingProfile ? (
            <input
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          ) : (
            <p className="text-sm text-gray-800">{username}</p>
          )}
        </div>

        {/* อีเมล */}
        <div className="mb-4">
          <p className="text-xs text-gray-500 mb-1 font-medium">อีเมล</p>
          <p className="text-sm text-gray-800">{user?.email}</p>
        </div>

        {/* ปุ่มบันทึก/ยกเลิก สำหรับแก้ไขโปรไฟล์ */}
        {editingProfile && (
          <div className="flex gap-2 mt-1 mb-4">
            <button
              onClick={handleSaveProfile}
              className="bg-indigo-500 hover:bg-indigo-600 text-white text-sm px-5 py-2 rounded-lg transition-colors"
            >
              บันทึก
            </button>
            <button
              onClick={() => { setEditingProfile(false); setUsername(user?.username || ''); }}
              className="border border-gray-300 text-gray-600 text-sm px-5 py-2 rounded-lg hover:bg-gray-50 transition-colors"
            >
              ยกเลิก
            </button>
          </div>
        )}

        {profileMsg && <p className="text-sm text-indigo-500 mb-4">{profileMsg}</p>}

        {/* ─── เปลี่ยนรหัสผ่าน (inline) ─── */}
        <div className="border-t border-gray-100 pt-4">
          <div className="flex items-center justify-between mb-1">
            <div>
              <p className="text-xs text-gray-500 font-medium">รหัสผ่าน</p>
              {pwStep === 'idle' && (
                <p className="text-xs text-gray-400 mt-0.5">ระบบจะส่ง OTP ไปยัง {user?.email}</p>
              )}
            </div>
            {pwStep === 'idle' && (
              <button
                onClick={handleSendOtp}
                disabled={pwLoading}
                className="text-sm bg-gray-100 hover:bg-gray-200 text-gray-700 px-4 py-1.5 rounded-lg transition-colors disabled:opacity-50"
              >
                {pwLoading ? 'กำลังส่ง...' : 'เปลี่ยนรหัสผ่าน'}
              </button>
            )}
          </div>

          {/* ── Step 1: กรอก OTP ── */}
          {pwStep === 'otp' && (
            <div className="space-y-3 mt-3">
              <p className="text-sm text-gray-600">
                กรอกรหัส OTP 6 หลักที่ส่งไปที่{' '}
                <span className="font-semibold text-indigo-600">{user?.email}</span>
              </p>
              <div className="flex gap-2">
                <input
                  type="text" maxLength={6} value={otpValue}
                  onChange={e => setOtpValue(e.target.value.replace(/\D/g, ''))}
                  placeholder="000000"
                  className="flex-1 border border-gray-300 rounded-lg px-4 py-2.5 text-sm text-center tracking-widest font-mono text-lg focus:outline-none focus:border-indigo-400"
                />
                <button
                  onClick={handleVerifyOtp}
                  disabled={otpValue.length !== 6 || pwLoading}
                  className="bg-indigo-500 hover:bg-indigo-600 text-white text-sm px-5 py-2 rounded-lg transition disabled:opacity-40"
                >
                  {pwLoading ? '...' : 'ยืนยัน'}
                </button>
              </div>
              <div className="flex items-center gap-3">
                <button
                  onClick={handleSendOtp}
                  disabled={countdown > 0 || pwLoading}
                  className="text-xs text-indigo-500 hover:underline disabled:text-gray-400 disabled:no-underline"
                >
                  {countdown > 0 ? `ส่งซ้ำได้ใน ${countdown}s` : 'ส่ง OTP ใหม่'}
                </button>
                <button onClick={cancelPwChange} className="text-xs text-gray-400 hover:text-gray-600">
                  ยกเลิก
                </button>
              </div>
            </div>
          )}

          {/* ── Step 2: ตั้งรหัสใหม่ ── */}
          {pwStep === 'newpass' && (
            <div className="space-y-3 mt-3">
              <div>
                <label className="text-xs font-medium text-gray-500 block mb-1">
                  รหัสผ่านใหม่ <span className="text-red-500">*</span>
                </label>
                <input
                  type="password" value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  placeholder="อย่างน้อย 8 ตัวอักษร"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-500 block mb-1">
                  ยืนยันรหัสผ่านใหม่ <span className="text-red-500">*</span>
                </label>
                <input
                  type="password" value={confirmPw}
                  onChange={e => setConfirmPw(e.target.value)}
                  placeholder="กรอกรหัสผ่านอีกครั้ง"
                  className={`w-full border rounded-lg px-3 py-2 text-sm focus:outline-none transition
                    ${confirmPw && confirmPw !== newPassword
                      ? 'border-red-400 focus:border-red-400'
                      : 'border-gray-300 focus:border-indigo-400'}`}
                />
                {confirmPw && confirmPw !== newPassword && (
                  <p className="text-xs text-red-500 mt-1">รหัสผ่านไม่ตรงกัน</p>
                )}
              </div>
              <div className="flex gap-2 pt-1">
                <button
                  onClick={handleResetPassword}
                  disabled={pwLoading || !newPassword || !confirmPw}
                  className="bg-indigo-500 hover:bg-indigo-600 text-white text-sm px-5 py-2 rounded-lg transition disabled:opacity-40"
                >
                  {pwLoading ? 'กำลังบันทึก...' : 'บันทึกรหัสผ่านใหม่'}
                </button>
                <button onClick={cancelPwChange}
                  className="border border-gray-300 text-gray-600 text-sm px-5 py-2 rounded-lg hover:bg-gray-50 transition">
                  ยกเลิก
                </button>
              </div>
            </div>
          )}

          {/* ข้อความแจ้งเตือน OTP/password */}
          {pwMsg.text && (
            <p className={`text-sm mt-3 ${pwMsg.ok ? 'text-indigo-500' : 'text-red-500'}`}>
              {pwMsg.text}
            </p>
          )}
        </div>
      </section>

      {/* ─── ส่วนที่ 2: ที่อยู่จัดส่ง ─── */}
      <section className="bg-white rounded-2xl shadow-sm p-7">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-gray-800">ที่อยู่จัดส่ง</h2>
          <button
            onClick={openAddForm}
            className="text-sm bg-gray-100 hover:bg-gray-200 text-gray-700 px-4 py-1.5 rounded-lg transition-colors"
          >
            + เพิ่มที่อยู่
          </button>
        </div>

        {addressMsg && <p className="text-sm text-indigo-500 mb-3">{addressMsg}</p>}

        {addresses.length === 0 && !showAddressForm && (
          <p className="text-sm text-gray-400 text-center py-5">
            ยังไม่มีที่อยู่ กด &quot;+ เพิ่มที่อยู่&quot; เพื่อเพิ่มเลยครับ
          </p>
        )}

        {/* รายการที่อยู่ */}
        <div className="flex flex-col gap-3">
          {addresses.map((addr) => (
            <div
              key={addr.id}
              className={`border rounded-xl p-4 transition-colors ${
                addr.isDefault ? 'border-indigo-400 bg-indigo-50' : 'border-gray-200'
              }`}
            >
              <div className="flex items-center gap-2 mb-1">
                <span className="font-semibold text-gray-800 text-sm">{addr.name}</span>
                {addr.isDefault && (
                  <span className="bg-indigo-500 text-white text-xs px-2 py-0.5 rounded-full">
                    default
                  </span>
                )}
              </div>
              <p className="text-xs text-gray-500">{addr.recipientName} · {addr.phone}</p>
              <p className="text-xs text-gray-500">
                {addr.address} {addr.subdistrict} {addr.district} {addr.province} {addr.postcode}
              </p>
              <div className="flex gap-3 mt-2">
                {!addr.isDefault && (
                  <button
                    onClick={() => handleSetDefault(addr.id)}
                    className="text-xs text-indigo-500 hover:underline"
                  >
                    ตั้งเป็น default
                  </button>
                )}
                <button
                  onClick={() => openEditForm(addr)}
                  className="text-xs text-indigo-500 hover:underline"
                >
                  แก้ไข
                </button>
                <button
                  onClick={() => handleDeleteAddress(addr.id)}
                  className="text-xs text-red-400 hover:underline"
                >
                  ลบ
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* ─── ฟอร์มเพิ่ม/แก้ไขที่อยู่ ─── */}
        {showAddressForm && (
          <div className="mt-5 pt-5 border-t border-gray-100">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-gray-700">
                {editingAddressId ? 'แก้ไขที่อยู่' : 'เพิ่มที่อยู่ใหม่'}
              </h3>
              {/* [Claude] ปุ่มเปิด MapPicker */}
              <button
                type="button"
                onClick={() => setShowMap(true)}
                className="flex items-center gap-1.5 text-sm bg-indigo-50 hover:bg-indigo-100 text-indigo-600 px-4 py-1.5 rounded-lg transition-colors border border-indigo-200"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                เลือกจากแผนที่
              </button>
            </div>

            <div className="grid grid-cols-2 gap-3 mb-3">
              {[
                { label: 'ชื่อที่อยู่ (เช่น บ้าน, ที่ทำงาน)', name: 'name' },
                { label: 'ชื่อผู้รับ',      name: 'recipientName', required: true },
                { label: 'เบอร์โทรศัพท์',  name: 'phone',          required: true },
                { label: 'ที่อยู่',          name: 'address' },
                { label: 'ตำบล / แขวง',    name: 'subdistrict' },
                { label: 'อำเภอ / เขต',    name: 'district' },
                { label: 'จังหวัด',         name: 'province' },
                { label: 'รหัสไปรษณีย์',   name: 'postcode' },
              ].map(({ label, name, required }) => {
                const isEmpty = required && !form[name].trim();
                return (
                  <div key={name} className="flex flex-col gap-1">
                    <label className="text-xs text-gray-500 font-medium">
                      {label}
                      {required && <span className="text-red-500 ml-0.5">*</span>}
                    </label>
                    <input
                      className={`border rounded-lg px-3 py-2 text-sm focus:outline-none transition
                        ${isEmpty
                          ? 'border-red-400 focus:border-red-500 bg-red-50'
                          : 'border-gray-300 focus:border-indigo-400'}`}
                      name={name}
                      value={form[name]}
                      onChange={handleFormChange}
                    />
                    {isEmpty && (
                      <p className="text-xs text-red-500">กรุณากรอก{label}</p>
                    )}
                  </div>
                );
              })}
            </div>

            <label className="flex items-center gap-2 text-sm text-gray-700 mb-4 cursor-pointer">
              <input
                type="checkbox"
                name="isDefault"
                checked={form.isDefault}
                onChange={handleFormChange}
              />
              ตั้งเป็นที่อยู่หลัก
            </label>

            <div className="flex gap-2">
              <button
                onClick={handleSaveAddress}
                disabled={!form.recipientName.trim() || !form.phone.trim()}
                className="bg-indigo-500 hover:bg-indigo-600 text-white text-sm px-5 py-2 rounded-lg transition-colors
                  disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-indigo-500"
              >
                บันทึก
              </button>
              <button
                onClick={() => { setShowAddressForm(false); setEditingAddressId(null); }}
                className="border border-gray-300 text-gray-600 text-sm px-5 py-2 rounded-lg hover:bg-gray-50 transition-colors"
              >
                ยกเลิก
              </button>
            </div>
          </div>
        )}
      </section>

      {/* [Claude] MapPicker modal — render เฉพาะเมื่อ showMap=true */}
      {showMap && (
        <MapPicker
          onConfirm={handleMapConfirm}
          onClose={() => setShowMap(false)}
        />
      )}

    </div>
  );
}
