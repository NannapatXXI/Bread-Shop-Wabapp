"use client";
import Image from 'next/image';
import { useState, useRef, useEffect, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import toast from 'react-hot-toast';
import { verifyOTP, sendOTPEmail } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';

function VerificationForm() {
  const router      = useRouter();
  const searchParams = useSearchParams();
  const email       = searchParams.get("email");

  const [otp, setOtp]         = useState(Array(6).fill(''));
  const [token, setToken]     = useState(null);
  const [error, setError]     = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const inputRefs = useRef([]);
  const length = 6;

  useEffect(() => {
    setToken(searchParams.get("token"));
  }, [searchParams]);

  const handleChange = (index, value) => {
    if (value && !/^\d$/.test(value)) return;
    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);
    if (value && index < length - 1) inputRefs.current[index + 1]?.focus();
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      const newOtp = [...otp];
      if (otp[index]) { newOtp[index] = ''; setOtp(newOtp); }
      else if (index > 0) { newOtp[index - 1] = ''; setOtp(newOtp); inputRefs.current[index - 1]?.focus(); }
    } else if (e.key === 'ArrowLeft' && index > 0) inputRefs.current[index - 1]?.focus();
    else if (e.key === 'ArrowRight' && index < length - 1) inputRefs.current[index + 1]?.focus();
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text/plain').slice(0, length).split('').filter(c => /^\d$/.test(c));
    const newOtp = [...otp];
    pasted.forEach((c, i) => { if (i < length) newOtp[i] = c; });
    setOtp(newOtp);
    const nextEmpty = newOtp.findIndex(v => !v);
    inputRefs.current[nextEmpty === -1 ? length - 1 : nextEmpty]?.focus();
  };

  const handleVerify = async () => {
    setError("");
    setIsLoading(true);
    try {
      const res = await verifyOTP({ token, otp: otp.join("") });
      router.push(`/reset-password?token=${res.data.token}`);
    } catch (err) {
      setError(err?.response?.data?.message ?? err?.message ?? "เกิดข้อผิดพลาดในระบบ");
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    setError("");
    try {
      const res = await sendOTPEmail({ email });
      router.replace(`/verify-email?token=${res.data.token}&email=${encodeURIComponent(email)}`);
      setToken(res.data.token);
      setOtp(Array(6).fill(""));
      toast.success("ส่ง OTP ใหม่เรียบร้อย");
    } catch (err) {
      setError(err?.response?.data?.message ?? err?.message ?? "เกิดข้อผิดพลาดในระบบ");
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

        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0B1F33', margin: '0 0 4px' }}>ยืนยัน OTP</h1>
        <p style={{ fontSize: '13px', color: '#5a7a9a', margin: '0 0 1.75rem', lineHeight: 1.6 }}>
          กรอกรหัส OTP 6 หลักที่ส่งไปยัง<br />
          <span style={{ color: '#0B1F33', fontWeight: 600 }}>{email}</span>
        </p>

        {/* OTP inputs */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginBottom: '1.5rem' }}>
          {otp.map((digit, index) => (
            <input key={index}
              ref={el => inputRefs.current[index] = el}
              type="text" inputMode="numeric" maxLength={1}
              value={digit}
              onChange={e => handleChange(index, e.target.value)}
              onKeyDown={e => handleKeyDown(index, e)}
              onPaste={handlePaste}
              onFocus={() => inputRefs.current[index]?.select()}
              style={{
                width: '44px', height: '52px', textAlign: 'center',
                fontSize: '20px', fontWeight: 600, color: '#0B1F33',
                background: 'white', border: `2px solid ${digit ? '#0B1F33' : '#c8d8e8'}`,
                borderRadius: '10px', outline: 'none', transition: 'border-color 0.15s',
                boxSizing: 'border-box',
              }}
            />
          ))}
        </div>

        {/* Resend */}
        <p style={{ textAlign: 'center', fontSize: '13px', color: '#8ba6ca', margin: '0 0 1rem' }}>
          ไม่ได้รับ OTP?{' '}
          <span onClick={handleResend} style={{ color: '#378ADD', cursor: 'pointer', fontWeight: 600 }}>
            ส่งใหม่
          </span>
        </p>

        {/* Error */}
        {error && (
          <div style={{ background: '#fff0f0', border: '1px solid #fcc', borderRadius: '8px', padding: '10px 14px', fontSize: '13px', color: '#cc3333', marginBottom: '1rem', textAlign: 'center' }}>
            {error}
          </div>
        )}

        {/* Buttons */}
        <div style={{ display: 'flex', gap: '10px' }}>
          <button type="button" onClick={() => router.push('/login')}
            style={{ flex: 1, padding: '12px', background: 'white', color: '#5a7a9a', border: '1.5px solid #c8d8e8', borderRadius: '10px', fontSize: '14px', fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' }}>
            ย้อนกลับ
          </button>
          <button type="button" onClick={handleVerify} disabled={isLoading || otp.some(d => !d)}
            style={{ flex: 2, padding: '12px', background: (isLoading || otp.some(d => !d)) ? '#3a5a7a' : '#0B1F33', color: '#A8CEFF', border: 'none', borderRadius: '10px', fontSize: '14px', fontWeight: 600, cursor: (isLoading || otp.some(d => !d)) ? 'not-allowed' : 'pointer', transition: 'background 0.2s', opacity: otp.some(d => !d) ? 0.5 : 1 }}>
            {isLoading
              ? <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}><Spinner size={16} color="#A8CEFF" /> กำลังตรวจสอบ...</span>
              : 'ยืนยัน OTP'}
          </button>
        </div>

      </div>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', color: '#8ba6ca' }}>กำลังโหลด...</div>}>
      <VerificationForm />
    </Suspense>
  );
}
