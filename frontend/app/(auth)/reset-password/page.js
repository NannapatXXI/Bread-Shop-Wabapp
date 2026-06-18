"use client";
import { useState, Suspense } from "react";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import Image from 'next/image';
import toast from 'react-hot-toast';
import { useSearchParams, useRouter } from "next/navigation";
import { resetPassword } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  const [newPassword, setNewPassword]         = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword]       = useState(false);
  const [showConfirm, setShowConfirm]         = useState(false);
  const [error, setError]                     = useState("");
  const [isLoading, setIsLoading]             = useState(false);

  const handleConfirm = async (e) => {
    e.preventDefault();
    setError("");

    if (!newPassword || !confirmPassword) { setError("กรุณากรอกข้อมูลให้ครบทุกช่อง"); return; }
    if (newPassword.length < 6 || (!/\d/.test(newPassword) && !/[^a-zA-Z0-9]/.test(newPassword))) {
      setError("รหัสผ่านต้องมีความยาวอย่างน้อย 6 ตัวอักษร และต้องมีตัวเลขหรือสัญลักษณ์");
      return;
    }
    if (newPassword !== confirmPassword) { setError("รหัสผ่านและยืนยันรหัสผ่านไม่ตรงกัน"); return; }

    setIsLoading(true);
    try {
      await resetPassword({ token, newPassword });
      toast.success('เปลี่ยนรหัสผ่านสำเร็จ! กำลังกลับไปหน้า Login...');
      setTimeout(() => router.push('/login'), 1500);
    } catch (err) {
      setError(err?.response?.data?.message ?? err?.message ?? "เกิดข้อผิดพลาดในระบบ");
    } finally {
      setIsLoading(false);
    }
  };

  const inputStyle = { width: '100%', padding: '11px 40px 11px 38px', fontSize: '14px', background: 'white', border: '1.5px solid #c8d8e8', borderRadius: '10px', boxSizing: 'border-box', color: '#0B1F33', outline: 'none' };

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

        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0B1F33', margin: '0 0 4px' }}>ตั้งรหัสผ่านใหม่</h1>
        <p style={{ fontSize: '13px', color: '#5a7a9a', margin: '0 0 1.75rem' }}>กรอกรหัสผ่านใหม่ที่ต้องการใช้</p>

        <form onSubmit={handleConfirm}>

          {/* New Password */}
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>รหัสผ่านใหม่</label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              <input type={showPassword ? 'text' : 'password'} placeholder="••••••••"
                value={newPassword} onChange={e => setNewPassword(e.target.value)}
                disabled={isLoading} style={inputStyle} />
              <button type="button" onClick={() => setShowPassword(!showPassword)}
                style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#8ba6ca' }}>
                {showPassword ? <FaEyeSlash size={16} /> : <FaEye size={16} />}
              </button>
            </div>
          </div>

          {/* Confirm Password */}
          <div style={{ marginBottom: '0.5rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px' }}>ยืนยันรหัสผ่านใหม่</label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              <input type={showConfirm ? 'text' : 'password'} placeholder="••••••••"
                value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
                disabled={isLoading} style={inputStyle} />
              <button type="button" onClick={() => setShowConfirm(!showConfirm)}
                style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#8ba6ca' }}>
                {showConfirm ? <FaEyeSlash size={16} /> : <FaEye size={16} />}
              </button>
            </div>
          </div>

          {/* Error */}
          {error && (
            <div style={{ background: '#fff0f0', border: '1px solid #fcc', borderRadius: '8px', padding: '10px 14px', fontSize: '13px', color: '#cc3333', margin: '1rem 0', textAlign: 'center' }}>
              {error}
            </div>
          )}

          {/* Buttons */}
          <div style={{ display: 'flex', gap: '10px', marginTop: '1.25rem' }}>
            <button type="button" onClick={() => router.push('/login')} style={{ flex: 1, padding: '12px', background: 'white', color: '#5a7a9a', border: '1.5px solid #c8d8e8', borderRadius: '10px', fontSize: '14px', fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' }}>
              ย้อนกลับ
            </button>
            <button type="submit" disabled={isLoading} style={{ flex: 2, padding: '12px', background: isLoading ? '#3a5a7a' : '#0B1F33', color: '#A8CEFF', border: 'none', borderRadius: '10px', fontSize: '14px', fontWeight: 600, cursor: isLoading ? 'not-allowed' : 'pointer', transition: 'background 0.2s' }}>
              {isLoading
              ? <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}><Spinner size={16} color="#A8CEFF" /> กำลังบันทึก...</span>
              : 'ยืนยันรหัสผ่านใหม่'}
            </button>
          </div>

        </form>
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>กำลังโหลด...</div>}>
      <ResetPasswordForm />
    </Suspense>
  );
}
