"use client";
import { useState } from "react";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import Image from 'next/image';
import Link from 'next/link';
import toast from 'react-hot-toast';
import { register } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import { useRouter } from 'next/navigation';

export default function RegisterPage() {
  const router = useRouter();

  const [username, setUsername]                 = useState("");
  const [email, setEmail]                       = useState("");
  const [password, setPassword]                 = useState("");
  const [confirmPassword, setConfirmPassword]   = useState("");
  const [showPassword, setShowPassword]         = useState(false);
  const [showConfirm, setShowConfirm]           = useState(false);
  const [error, setError]                       = useState("");
  const [isLoading, setIsLoading]               = useState(false);

  const handleConfirm = async (e) => {
    e.preventDefault();
    setError("");

    if (!username || !email || !password || !confirmPassword) {
      setError("กรุณากรอกข้อมูลให้ครบทุกช่อง");
      return;
    }
    if (username.length < 4 || username.length > 20 || !/^[a-zA-Z0-9]+$/.test(username)) {
      setError("ชื่อผู้ใช้ต้องมีความยาว 4-20 ตัวอักษร และไม่มีอักขระพิเศษ");
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError("กรุณาใช้อีเมลที่ถูกต้อง เช่น yourname@example.com");
      return;
    }
    if (password.length < 6 || (!/\d/.test(password) && !/[^a-zA-Z0-9]/.test(password))) {
      setError("รหัสผ่านต้องมีความยาวอย่างน้อย 6 ตัวอักษร และต้องมีตัวเลขหรือสัญลักษณ์");
      return;
    }
    if (password !== confirmPassword) {
      setError("รหัสผ่านและยืนยันรหัสผ่านไม่ตรงกัน");
      return;
    }

    setIsLoading(true);
    try {
      await register({ username, email, password });
      toast.success('ลงทะเบียนสำเร็จ! กำลังกลับไปหน้า Login...');
      router.push('/login');
    } catch (err) {
      setError(err?.response?.data?.message || "ไม่สามารถเชื่อมต่อ Server ได้");
    } finally {
      setIsLoading(false);
    }
  };

  const inputStyle = {
    width: '100%', padding: '11px 12px 11px 38px',
    fontSize: '14px', background: 'white',
    border: '1.5px solid #c8d8e8', borderRadius: '10px',
    boxSizing: 'border-box', color: '#0B1F33', outline: 'none',
  };

  return (
    <div>
      {/* decorative circles */}
      <div style={{
        position: 'fixed', top: '-80px', right: '-80px',
        width: '300px', height: '300px', borderRadius: '50%',
        background: 'rgba(58,123,213,0.12)', pointerEvents: 'none',
      }} />
      <div style={{
        position: 'fixed', bottom: '-60px', left: '-60px',
        width: '220px', height: '220px', borderRadius: '50%',
        background: 'rgba(168,206,255,0.07)', pointerEvents: 'none',
      }} />

      <div style={{
        background: '#EEF4FB', borderRadius: '20px', padding: '2.5rem',
        width: '100%', boxSizing: 'border-box',
      }}>

        {/* Logo + title */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '1.5rem' }}>
          <Image src="/logo.png" alt="Peak Pung Logo" width={52} height={52}
            style={{ objectFit: 'cover', flexShrink: 0, borderRadius: '50%' }} />
          <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', marginTop: '8px' }}>
            <p style={{ margin: 0, fontSize: '15px', fontWeight: 700, color: '#0B1F33' }}>Peak Pung</p>
            <p style={{ margin: 0, fontSize: '12px', color: '#8ba6ca', whiteSpace: 'nowrap' }}>by Mom Hmee</p>
          </div>
        </div>

        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0B1F33', margin: '0 0 4px' }}>สมัครสมาชิก</h1>
        <p style={{ fontSize: '13px', color: '#5a7a9a', margin: '0 0 1.5rem' }}>สร้างบัญชีเพื่อเริ่มสั่งซื้อสินค้า</p>

        <form onSubmit={handleConfirm}>

          {/* Username */}
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>
              Username
            </label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
              <input type="text" placeholder="ชื่อผู้ใช้" value={username}
                onChange={e => setUsername(e.target.value)} disabled={isLoading}
                style={inputStyle} />
            </div>
          </div>

          {/* Email */}
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>
              Email
            </label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                <polyline points="22,6 12,13 2,6" />
              </svg>
              <input type="email" placeholder="your@email.com" value={email}
                onChange={e => setEmail(e.target.value)} disabled={isLoading}
                style={inputStyle} />
            </div>
          </div>

          {/* Password */}
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              <input type={showPassword ? 'text' : 'password'} placeholder="••••••••" value={password}
                onChange={e => setPassword(e.target.value)} disabled={isLoading}
                style={{ ...inputStyle, paddingRight: '40px' }} />
              <button type="button" onClick={() => setShowPassword(!showPassword)}
                style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#8ba6ca' }}>
                {showPassword ? <FaEyeSlash size={16} /> : <FaEye size={16} />}
              </button>
            </div>
          </div>

          {/* Confirm Password */}
          <div style={{ marginBottom: '0.5rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>
              ยืนยัน Password
            </label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              <input type={showConfirm ? 'text' : 'password'} placeholder="••••••••" value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)} disabled={isLoading}
                style={{ ...inputStyle, paddingRight: '40px' }} />
              <button type="button" onClick={() => setShowConfirm(!showConfirm)}
                style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#8ba6ca' }}>
                {showConfirm ? <FaEyeSlash size={16} /> : <FaEye size={16} />}
              </button>
            </div>
          </div>

          {/* Error */}
          {error && (
            <div style={{
              background: '#fff0f0', border: '1px solid #fcc', borderRadius: '8px',
              padding: '10px 14px', fontSize: '13px', color: '#cc3333',
              margin: '1rem 0', textAlign: 'center',
            }}>
              {error}
            </div>
          )}

          {/* Buttons */}
          <div style={{ display: 'flex', gap: '10px', marginTop: '1.25rem' }}>
            <Link href="/login" style={{ flex: 1 }}>
              <button type="button" style={{
                width: '100%', padding: '12px',
                background: 'white', color: '#5a7a9a',
                border: '1.5px solid #c8d8e8', borderRadius: '10px',
                fontSize: '14px', fontWeight: 600, cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}>
                ย้อนกลับ
              </button>
            </Link>
            <button type="submit" disabled={isLoading} style={{
              flex: 2, padding: '12px',
              background: isLoading ? '#3a5a7a' : '#0B1F33',
              color: '#A8CEFF', border: 'none', borderRadius: '10px',
              fontSize: '14px', fontWeight: 600,
              cursor: isLoading ? 'not-allowed' : 'pointer',
              transition: 'background 0.2s',
            }}>
              {isLoading
                ? <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}><Spinner size={16} color="#A8CEFF" /> กำลังสมัคร...</span>
                : 'สมัครสมาชิก'}
            </button>
          </div>

        </form>

        <p style={{ textAlign: 'center', fontSize: '12px', color: '#8ba6ca', marginTop: '1.25rem', marginBottom: 0 }}>
          มีบัญชีอยู่แล้วใช่ไหม?{' '}
          <Link href="/login">
            <span style={{ color: '#378ADD', cursor: 'pointer', fontWeight: 600 }}>เข้าสู่ระบบ</span>
          </Link>
        </p>

      </div>
    </div>
  );
}
