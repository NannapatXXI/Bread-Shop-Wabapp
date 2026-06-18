"use client";
import Image from 'next/image';
import { useState } from "react";
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { sendOTPEmail } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail]       = useState("");
  const [error, setError]       = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async () => {
    const trimmed = email.trim();
    if (!trimmed) { setError("กรุณากรอก Email"); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) { setError("รูปแบบอีเมลไม่ถูกต้อง"); return; }

    setIsLoading(true);
    setError("");
    try {
      const res = await sendOTPEmail({ email: trimmed });
      toast.success("ส่งอีเมลสำเร็จ");
      router.push(`/verify-email?token=${res.data.token}&email=${encodeURIComponent(trimmed)}`);
    } catch (err) {
      setError(err?.response?.data?.message ?? err?.message ?? "เกิดข้อผิดพลาดในระบบ");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div>
      {/* decorative circles */}
      <div style={{ position: 'fixed', top: '-80px', right: '-80px', width: '300px', height: '300px', borderRadius: '50%', background: 'rgba(58,123,213,0.12)', pointerEvents: 'none' }} />
      <div style={{ position: 'fixed', bottom: '-60px', left: '-60px', width: '220px', height: '220px', borderRadius: '50%', background: 'rgba(168,206,255,0.07)', pointerEvents: 'none' }} />

      <div style={{ background: '#EEF4FB', borderRadius: '20px', padding: 'clamp(1.5rem, 6vw, 2.5rem)', width: '100%', boxSizing: 'border-box' }}>

        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '1.5rem' }}>
          <Image src="/logo.png" alt="Peak Pung Logo" width={52} height={52} style={{ objectFit: 'cover', flexShrink: 0, borderRadius: '50%' }} />
          <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', marginTop: '8px' }}>
            <p style={{ margin: 0, fontSize: '15px', fontWeight: 700, color: '#0B1F33' }}>Peak Pung</p>
            <p style={{ margin: 0, fontSize: '12px', color: '#8ba6ca', whiteSpace: 'nowrap' }}>by Mom Hmee</p>
          </div>
        </div>

        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0B1F33', margin: '0 0 4px' }}>ลืมรหัสผ่าน</h1>
        <p style={{ fontSize: '13px', color: '#5a7a9a', margin: '0 0 1.75rem' }}>กรอกอีเมลเพื่อรับลิงก์ตั้งรหัสผ่านใหม่</p>

        {/* Email input */}
        <div style={{ marginBottom: '1rem' }}>
          <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>Email</label>
          <div style={{ position: 'relative' }}>
            <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
              <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
              <polyline points="22,6 12,13 2,6" />
            </svg>
            <input type="email" placeholder="your@email.com" value={email}
              onChange={e => setEmail(e.target.value)} disabled={isLoading}
              onKeyDown={e => e.key === 'Enter' && handleSubmit()}
              style={{ width: '100%', padding: '11px 12px 11px 38px', fontSize: '14px', background: 'white', border: '1.5px solid #c8d8e8', borderRadius: '10px', boxSizing: 'border-box', color: '#0B1F33', outline: 'none' }} />
          </div>
        </div>

        {/* Error */}
        {error && (
          <div style={{ background: '#fff0f0', border: '1px solid #fcc', borderRadius: '8px', padding: '10px 14px', fontSize: '13px', color: '#cc3333', marginBottom: '1rem', textAlign: 'center' }}>
            {error}
          </div>
        )}

        {/* Buttons */}
        <div style={{ display: 'flex', gap: '10px', marginTop: '1.25rem' }}>
          <button type="button" onClick={() => router.push('/login')} style={{ flex: 1, padding: '12px', background: 'white', color: '#5a7a9a', border: '1.5px solid #c8d8e8', borderRadius: '10px', fontSize: '14px', fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' }}>
            ย้อนกลับ
          </button>
          <button type="button" onClick={handleSubmit} disabled={isLoading} style={{ flex: 2, padding: '12px', background: isLoading ? '#3a5a7a' : '#0B1F33', color: '#A8CEFF', border: 'none', borderRadius: '10px', fontSize: '14px', fontWeight: 600, cursor: isLoading ? 'not-allowed' : 'pointer', transition: 'background 0.2s' }}>
            {isLoading
              ? <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}><Spinner size={16} color="#A8CEFF" /> กำลังส่ง...</span>
              : 'ส่งอีเมล'}
          </button>
        </div>

      </div>
    </div>
  );
}
