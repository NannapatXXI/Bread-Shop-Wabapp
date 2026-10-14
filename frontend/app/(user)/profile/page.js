'use client';

// [Claude] หน้า Profile — แสดง/แก้ไขข้อมูลส่วนตัว และจัดการที่อยู่จัดส่ง
// รองรับการเลือกที่อยู่จากแผนที่ผ่าน MapPicker (Leaflet + OpenStreetMap)

import { useCallback, useEffect, useState, useRef } from 'react';
import dynamic from 'next/dynamic';
import { useAuth } from '../../context/AuthContext';
import api from '@/lib/api';
import { sendOTPEmail, verifyOTP, resetPassword } from '@/services/auth.service';
import Spinner from '@/app/components/Spinner';
import Icon from '@/app/components/Icon';
import PasswordField from '@/app/components/PasswordField';

// [Claude] dynamic import เพราะ Leaflet ใช้ window ซึ่งไม่มีใน SSR
const MapPicker = dynamic(() => import('../../components/MapPicker'), { ssr: false });

export default function ProfilePage() {
  const { user, loading, logout } = useAuth();

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

  //  ดึงรายการที่อยู่ของ user จาก backend
  const userId = user?.id;
  const fetchAddresses = useCallback(async () => {
    try {
      const res = await api.get(`/api/users/${userId}/addresses`);
      setAddresses(res.data);
    } catch {
      setAddressMsg('โหลดที่อยู่ไม่สำเร็จ');
    }
  }, [userId]);

  // ─── โหลดข้อมูลเริ่มต้น ──────────────────────────────────────
  useEffect(() => {
    if (!user) return;
    setUsername(user.username || '');
    fetchAddresses();
  }, [user, fetchAddresses]);

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

  // เปิดฟอร์มแก้ไขที่อยู่ที่มีอยู่แล้ว
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

  // รับค่าที่อยู่จาก MapPicker แล้วกรอกลงฟอร์ม
  // spread โดยตรง ไม่ใช้ || fallback เพื่อให้ค่าที่ map ดึงมา override ฟอร์มเสมอ
  // (name/recipientName/phone ไม่ถูกแตะเพราะ addressData ไม่มี key พวกนั้น)
  const handleMapConfirm = (addressData) => {
    setForm(prev => ({ ...prev, ...addressData }));
    setShowMap(false);
  };

  //  บันทึกที่อยู่ — ถ้ามี editingAddressId คือแก้ไข, ไม่มีคือเพิ่มใหม่
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

  //  ลบที่อยู่
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

  // [ตั้ง default ที่อยู่
  const handleSetDefault = async (addressId) => {
    try {
      await api.put(`/api/users/${user.id}/addresses/${addressId}/default`);
      fetchAddresses();
    } catch {
      setAddressMsg('ตั้งค่าไม่สำเร็จ');
      setTimeout(() => setAddressMsg(''), 3000);
    }
  };

  if (loading) return (
    <div style={{ maxWidth: 860, margin: '0 auto' }}>
      <div className="page-head"><div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <div className="sk" style={{ width: 64, height: 64, borderRadius: '50%' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}><div className="sk" style={{ height: 20, width: 180 }} /><div className="sk" style={{ height: 14, width: 240 }} /></div>
      </div></div>
      <div className="card pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {Array.from({ length: 3 }).map((_, i) => <div key={i} className="sk" style={{ height: 40 }} />)}
      </div>
    </div>
  );

  const textInput = (props) => <div className="control"><input {...props} /></div>;

  return (
    <div style={{ maxWidth: 860, margin: '0 auto' }}>
      <div className="page-head">
        <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
          <span className="avatar" style={{ width: 64, height: 64, fontSize: 24 }}>{username.charAt(0).toUpperCase()}</span>
          <div><h1>{username}</h1><p>{user?.email}</p></div>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

        {/* ─── ข้อมูลส่วนตัว ─── */}
        <section className="card">
          <div className="card-head">
            <h2>ข้อมูลส่วนตัว</h2>
            {!editingProfile && pwStep === 'idle' && (
              <button className="btn btn-outline sm" onClick={() => setEditingProfile(true)}><Icon name="edit" size="sm" />แก้ไข</button>
            )}
          </div>
          <div className="pad" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="grid-2">
              <div className="field">
                <label htmlFor="pf-username" className="label muted" style={{ fontWeight: 500, fontSize: 13 }}>ชื่อผู้ใช้</label>
                {editingProfile
                  ? textInput({ id: 'pf-username', value: username, onChange: (e) => setUsername(e.target.value) })
                  : <div>{username}</div>}
              </div>
              <div>
                <div className="label muted" style={{ fontWeight: 500, fontSize: 13 }}>อีเมล</div>
                <div style={{ overflowWrap: 'anywhere' }}>{user?.email}</div>
              </div>
            </div>
            {editingProfile && (
              <div style={{ display: 'flex', gap: 10 }}>
                <button className="btn btn-primary sm" onClick={handleSaveProfile}>บันทึก</button>
                <button className="btn btn-outline sm" onClick={() => { setEditingProfile(false); setUsername(user?.username || ''); }}>ยกเลิก</button>
              </div>
            )}
            {profileMsg && <p className={profileMsg.includes('ไม่สำเร็จ') ? 'alert-error' : 'alert-ok'} style={{ margin: 0 }}>{profileMsg}</p>}
          </div>
        </section>

        {/* ─── ความปลอดภัย / เปลี่ยนรหัสผ่าน ─── */}
        <section className="card">
          <div className="card-head"><h2>ความปลอดภัย</h2></div>
          <div className="pad" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
              <div>
                <b style={{ fontWeight: 600 }}>รหัสผ่าน</b>
                {pwStep === 'idle' && <div className="hint">ระบบจะส่ง OTP ไปที่ {user?.email} เพื่อยืนยัน</div>}
              </div>
              {pwStep === 'idle' && (
                <button className="btn btn-outline sm" onClick={handleSendOtp} disabled={pwLoading}>
                  {pwLoading ? <><Spinner size={14} /> กำลังส่ง...</> : <><Icon name="lock" size="sm" />เปลี่ยนรหัสผ่าน</>}
                </button>
              )}
            </div>

            {pwStep === 'otp' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <p style={{ margin: 0, fontSize: 14 }}>กรอกรหัส OTP 6 หลักที่ส่งไปที่ <b>{user?.email}</b></p>
                <div style={{ display: 'flex', gap: 10 }}>
                  <div className="control" style={{ flex: 1 }}>
                    <input type="text" maxLength={6} value={otpValue} inputMode="numeric" aria-label="รหัส OTP"
                      onChange={e => setOtpValue(e.target.value.replace(/\D/g, ''))} placeholder="000000"
                      style={{ textAlign: 'center', letterSpacing: '.4em', fontFamily: 'var(--font-head)', fontSize: 18 }} />
                  </div>
                  <button className="btn btn-primary" onClick={handleVerifyOtp} disabled={otpValue.length !== 6 || pwLoading}>
                    {pwLoading ? <Spinner size={16} /> : 'ยืนยัน'}
                  </button>
                </div>
                <div style={{ display: 'flex', gap: 16, fontSize: 14 }}>
                  <button className="link" style={{ background: 'none', border: 0, padding: 0, color: countdown > 0 || pwLoading ? 'var(--faint)' : undefined }}
                    onClick={handleSendOtp} disabled={countdown > 0 || pwLoading}>
                    {countdown > 0 ? `ส่งซ้ำได้ใน ${countdown}s` : 'ส่ง OTP ใหม่'}
                  </button>
                  <button className="muted" style={{ background: 'none', border: 0, padding: 0 }} onClick={cancelPwChange}>ยกเลิก</button>
                </div>
              </div>
            )}

            {pwStep === 'newpass' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <PasswordField id="pf-newpw" label="รหัสผ่านใหม่" placeholder="อย่างน้อย 8 ตัวอักษร"
                  value={newPassword} onChange={e => setNewPassword(e.target.value)} />
                <PasswordField id="pf-confirmpw" label="ยืนยันรหัสผ่านใหม่" placeholder="กรอกรหัสผ่านอีกครั้ง"
                  value={confirmPw} onChange={e => setConfirmPw(e.target.value)} />
                {confirmPw && confirmPw !== newPassword && (
                  <p style={{ margin: '-6px 0 0', fontSize: 13, color: 'var(--red)' }}>รหัสผ่านไม่ตรงกัน</p>
                )}
                <div style={{ display: 'flex', gap: 10 }}>
                  <button className="btn btn-primary sm" onClick={handleResetPassword} disabled={pwLoading || !newPassword || !confirmPw}>
                    {pwLoading ? <><Spinner size={14} /> กำลังบันทึก...</> : 'บันทึกรหัสผ่านใหม่'}
                  </button>
                  <button className="btn btn-outline sm" onClick={cancelPwChange}>ยกเลิก</button>
                </div>
              </div>
            )}

            {pwMsg.text && <p className={pwMsg.ok ? 'alert-ok' : 'alert-error'} style={{ margin: 0 }}>{pwMsg.text}</p>}
          </div>
        </section>

        {/* ─── ที่อยู่จัดส่ง ─── */}
        <section className="card">
          <div className="card-head">
            <h2>ที่อยู่จัดส่ง</h2>
            <button className="btn btn-outline sm" onClick={openAddForm}><Icon name="plus" size="sm" />เพิ่มที่อยู่</button>
          </div>
          <div className="pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {addressMsg && <p className={addressMsg.includes('ไม่สำเร็จ') ? 'alert-error' : 'alert-ok'} style={{ margin: 0 }}>{addressMsg}</p>}

            {addresses.length === 0 && !showAddressForm && (
              <div className="empty compact">
                <div className="ic"><Icon name="pin" /></div>
                <h3>ยังไม่มีที่อยู่</h3>
                <p>เพิ่มที่อยู่ไว้ จะได้สั่งขนมได้เร็วขึ้น</p>
              </div>
            )}

            {addresses.map((addr) => (
              <div key={addr.id} className={`opt${addr.isDefault ? ' on' : ''}`} style={{ cursor: 'default', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <b style={{ fontWeight: 600 }}>{addr.name}</b>
                  {addr.isDefault && <span className="badge b-blue plain">ที่อยู่หลัก</span>}
                </div>
                <div className="muted" style={{ fontSize: 14 }}>{addr.recipientName} · {addr.phone}</div>
                <div className="muted" style={{ fontSize: 14 }}>
                  {addr.address} {addr.subdistrict} {addr.district} {addr.province} {addr.postcode}
                </div>
                <div style={{ display: 'flex', gap: 4, marginTop: 4, marginLeft: -12 }}>
                  {!addr.isDefault && (
                    <button className="btn btn-ghost sm" onClick={() => handleSetDefault(addr.id)}>ตั้งเป็นที่อยู่หลัก</button>
                  )}
                  <button className="btn btn-ghost sm" onClick={() => openEditForm(addr)}><Icon name="edit" size="sm" />แก้ไข</button>
                  <button className="btn btn-ghost sm btn-danger-text" onClick={() => handleDeleteAddress(addr.id)}><Icon name="trash" size="sm" />ลบ</button>
                </div>
              </div>
            ))}

            {showAddressForm && (
              <div style={{ borderTop: addresses.length ? '1px solid var(--line)' : 0, paddingTop: addresses.length ? 16 : 0, display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                  <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{editingAddressId ? 'แก้ไขที่อยู่' : 'เพิ่มที่อยู่ใหม่'}</h3>
                  <button type="button" className="btn btn-outline sm" onClick={() => setShowMap(true)}><Icon name="pin" size="sm" />เลือกจากแผนที่</button>
                </div>

                <div className="grid-2">
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
                      <div key={name} className="field">
                        <label htmlFor={`addr-${name}`}>{label}{required && <span className="req"> *</span>}</label>
                        <div className="control" style={isEmpty ? { borderColor: 'var(--red)' } : undefined}>
                          <input id={`addr-${name}`} name={name} value={form[name]} onChange={handleFormChange} />
                        </div>
                        {isEmpty && <span style={{ fontSize: 13, color: 'var(--red)' }}>กรุณากรอก{label}</span>}
                      </div>
                    );
                  })}
                </div>

                <label className="check">
                  <input type="checkbox" name="isDefault" checked={form.isDefault} onChange={handleFormChange} />
                  ตั้งเป็นที่อยู่หลัก
                </label>

                <div style={{ display: 'flex', gap: 10 }}>
                  <button className="btn btn-primary sm" onClick={handleSaveAddress} disabled={!form.recipientName.trim() || !form.phone.trim()}>บันทึก</button>
                  <button className="btn btn-outline sm" onClick={() => { setShowAddressForm(false); setEditingAddressId(null); }}>ยกเลิก</button>
                </div>
              </div>
            )}
          </div>
        </section>

        <button className="btn btn-ghost" onClick={logout} style={{ alignSelf: 'flex-start', color: 'var(--red)' }}>
          <Icon name="logout" size="sm" />ออกจากระบบ
        </button>
      </div>

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
