"use client";
import { useState } from "react";
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { sendOTPEmail } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import Icon from '@/app/components/Icon';

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
      const res = await sendOTPEmail(trimmed);
      toast.success("ส่งอีเมลสำเร็จ");
      router.push(`/verify-email?token=${res.data.token}&email=${encodeURIComponent(trimmed)}`);
    } catch (err) {
      setError(err?.response?.data?.message ?? err?.message ?? "เกิดข้อผิดพลาดในระบบ");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <>
      <button type="button" className="btn btn-ghost sm" style={{ margin: '0 0 20px -12px' }} onClick={() => router.push('/login')}>
        <Icon name="left" size="sm" />กลับไปหน้าเข้าสู่ระบบ
      </button>
      <h1>ลืมรหัสผ่าน</h1>
      <p>กรอกอีเมลที่ใช้สมัคร เราจะส่งรหัส OTP ไปให้</p>
      <form noValidate onSubmit={e => { e.preventDefault(); handleSubmit(); }}>
        <div className="field">
          <label htmlFor="fp-email">อีเมล</label>
          <div className="control">
            <Icon name="mail" size="sm" />
            <input id="fp-email" type="email" placeholder="you@email.com" value={email}
              onChange={e => setEmail(e.target.value)} disabled={isLoading} />
          </div>
        </div>

        {error && <div className="alert-error" role="alert">{error}</div>}

        <button type="submit" className="btn btn-primary btn-block" disabled={isLoading}>
          {isLoading ? <><Spinner size={16} /> กำลังส่ง...</> : 'ส่งรหัส OTP'}
        </button>
      </form>
    </>
  );
}
