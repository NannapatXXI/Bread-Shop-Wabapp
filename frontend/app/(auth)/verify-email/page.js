"use client";
import { useState, useRef, useEffect, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import toast from 'react-hot-toast';
import { verifyOTP, sendOTPEmail } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import Icon from '@/app/components/Icon';

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
      const res = await sendOTPEmail(email);
      router.replace(`/verify-email?token=${res.data.token}&email=${encodeURIComponent(email)}`);
      setToken(res.data.token);
      setOtp(Array(6).fill(""));
      toast.success("ส่ง OTP ใหม่เรียบร้อย");
    } catch (err) {
      setError(err?.response?.data?.message ?? err?.message ?? "เกิดข้อผิดพลาดในระบบ");
    }
  };

  return (
    <>
      <button type="button" className="btn btn-ghost sm" style={{ margin: '0 0 20px -12px' }} onClick={() => router.push('/login')}>
        <Icon name="left" size="sm" />กลับไปหน้าเข้าสู่ระบบ
      </button>
      <h1>กรอกรหัส OTP</h1>
      <p>เราส่งรหัส 6 หลักไปที่ <b style={{ color: 'var(--ink)' }}>{email}</b></p>
      <form onSubmit={e => { e.preventDefault(); handleVerify(); }}>
        <div className="otp" aria-label="รหัส OTP 6 หลัก">
          {otp.map((digit, index) => (
            <input key={index}
              ref={el => inputRefs.current[index] = el}
              type="text" inputMode="numeric" maxLength={1}
              value={digit}
              aria-label={`หลักที่ ${index + 1}`}
              onChange={e => handleChange(index, e.target.value)}
              onKeyDown={e => handleKeyDown(index, e)}
              onPaste={handlePaste}
              onFocus={() => inputRefs.current[index]?.select()}
            />
          ))}
        </div>
        <p className="hint" style={{ margin: 0 }}>
          ไม่ได้รับรหัส? <button type="button" className="link" style={{ background: 'none', border: 0, padding: 0 }} onClick={handleResend}>ส่งใหม่</button>
        </p>

        {error && <div className="alert-error" role="alert">{error}</div>}

        <button type="submit" className="btn btn-primary btn-block" disabled={isLoading || otp.some(d => !d)}>
          {isLoading ? <><Spinner size={16} /> กำลังตรวจสอบ...</> : 'ยืนยันรหัส'}
        </button>
      </form>
    </>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<p className="muted">กำลังโหลด...</p>}>
      <VerificationForm />
    </Suspense>
  );
}
