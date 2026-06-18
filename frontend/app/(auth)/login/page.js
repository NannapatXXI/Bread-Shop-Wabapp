"use client";
import Link from 'next/link';
import Image from 'next/image';
import { useState } from "react";
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { login, profile } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import api from "@/lib/api";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const router = useRouter();

  const handleGoogleLogin = async () => {
    try {
      // [Fix] ใช้ api (axios) แทน fetch เพื่อให้ interceptor unwrap ApiResponse ก่อน
      // ถ้าใช้ fetch โดยตรง data.url จะเป็น undefined เพราะ URL จริงซ่อนอยู่ใน data.data.url
      const res = await api.get("/api/v1/auth/google");
      window.location.href = res.data.url;
    } catch {
      toast.error("ไม่สามารถเชื่อมต่อ Google ได้");
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);

    if (!email && !password) {
      setError("กรุณากรอก Email และ Password");
      setIsLoading(false);
      return;
    }
    if (!email) {
      setError("กรุณากรอก Email");
      setIsLoading(false);
      return;
    }
    if (!password) {
      setError("กรุณากรอก Password");
      setIsLoading(false);
      return;
    }

    try {
      const payload = { usernameOrEmail: email, password: password };
      await login(payload);
      await profile();
      router.push('/home');
      toast.success('เข้าสู่ระบบสำเร็จ!');
    } catch (err) {
      if (err.response) {
        const msg = err.response.data?.message || "";
        if (msg === "GOOGLE_ACCOUNT") {
          setError("บัญชีนี้ใช้ Google Login กรุณาเข้าสู่ระบบด้วยปุ่ม Google ด้านล่าง");
        } else {
          setError(msg || "อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        }
      } else {
        setError("ไม่สามารถเชื่อมต่อ Server ได้");
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div >

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
        background: '#EEF4FB',
        borderRadius: '20px',
        padding: 'clamp(1.5rem, 6vw, 3rem)',
        width: '100%',
        boxSizing: 'border-box',
      }}>

        {/* Logo + title */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '1.75rem' }}>
          <Image src="/logo.png" alt="Peak Pung Logo" width={56} height={56} style={{ objectFit: 'cover', flexShrink: 0, borderRadius: '50%' }} />
          <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', marginTop: '10px' }}>
            <p style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: '#0B1F33' }}>Peak Pung</p>
            <p style={{ margin: 0, fontSize: '12px', color: '#8ba6ca' }}>by Mom Hmee</p>
          </div>
        </div>

        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0B1F33', margin: '0 0 4px' }}>เข้าสู่ระบบ</h1>
        <p style={{ fontSize: '13px', color: '#5a7a9a', margin: '0 0 1.75rem' }}>สั่งซื้อสินค้าและติดตาม</p>

        <form onSubmit={handleSubmit}>

          {/* Email */}
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px', letterSpacing: '0.02em' }}>
              Username หรือ Email
            </label>
            <div style={{ position: 'relative' }}>
            <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px', color: '#8ba6ca' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
              <input
                type="text"
                placeholder="Username or Email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isLoading}
                style={{
                  width: '100%', padding: '11px 12px 11px 38px',
                  fontSize: '14px', background: 'white',
                  border: '1.5px solid #c8d8e8', borderRadius: '10px',
                  boxSizing: 'border-box', color: '#0B1F33', outline: 'none',
                }}
              />
            </div>
          </div>

          {/* Password */}
          <div style={{ marginBottom: '0.5rem' }}>
            <label style={{ fontSize: '12px', fontWeight: 600, color: '#0B1F33', display: 'block', marginBottom: '6px', letterSpacing: '0.02em' }}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <svg style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
                style={{
                  width: '100%', padding: '11px 40px 11px 38px',
                  fontSize: '14px', background: 'white',
                  border: '1.5px solid #c8d8e8', borderRadius: '10px',
                  boxSizing: 'border-box', color: '#0B1F33', outline: 'none',
                }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)',
                  background: 'none', border: 'none', cursor: 'pointer', fontSize: '16px', color: '#8ba6ca',
                }}
              >
               {showPassword ? (
                  <svg viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5" style={{ width: '16px', height: '16px' }}>
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="none" stroke="#8ba6ca" strokeWidth="1.5" style={{ width: '16px', height: '16px' }}>
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                )}
              </button>
            </div>
          </div>

          {/* Forgot password */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '1.25rem' }}>
            <Link href="/forgot-password">
              <span style={{ fontSize: '12px', color: '#378ADD', cursor: 'pointer', textDecoration: 'none' }}>
                ลืมรหัสผ่าน?
              </span>
            </Link>
          </div>

          {/* Error */}
          {error && (
            <div style={{
              background: '#fff0f0', border: '1px solid #fcc', borderRadius: '8px',
              padding: '10px 14px', fontSize: '13px', color: '#cc3333',
              marginBottom: '1rem', textAlign: 'center',
            }}>
              {error}
            </div>
          )}

          {/* Login button */}
          <button
            type="submit"
            disabled={isLoading}
            style={{
              width: '100%', padding: '12px',
              background: isLoading ? '#3a5a7a' : '#0B1F33',
              color: '#A8CEFF', border: 'none', borderRadius: '10px',
              fontSize: '14px', fontWeight: 600, cursor: isLoading ? 'not-allowed' : 'pointer',
              marginBottom: '1rem', letterSpacing: '0.03em', transition: 'background 0.2s',
            }}
          >
            {isLoading
              ? <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}><Spinner size={16} color="#A8CEFF" /> กำลังเข้าสู่ระบบ...</span>
              : 'เข้าสู่ระบบ'}
          </button>

        </form>

        {/* Divider */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem' }}>
          <div style={{ flex: 1, height: '1px', background: '#c8d8e8' }} />
          <span style={{ fontSize: '12px', color: '#8ba6ca' }}>หรือเข้าสู่ระบบด้วย</span>
          <div style={{ flex: 1, height: '1px', background: '#c8d8e8' }} />
        </div>

        {/* Google button */}
        <button
          type="button"
          onClick={handleGoogleLogin}
          style={{
            width: '100%', padding: '11px', background: 'white',
            border: '1.5px solid #c8d8e8', borderRadius: '10px',
            fontSize: '14px', color: '#0B1F33', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            gap: '8px', boxSizing: 'border-box', fontWeight: 500,
          }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
          </svg>
          เข้าสู่ระบบด้วย Google
        </button>

        {/* Sign up */}
        <p style={{ textAlign: 'center', fontSize: '12px', color: '#8ba6ca', marginTop: '1.25rem', marginBottom: 0 }}>
          ยังไม่มีบัญชี?{' '}
          <Link href="/register">
            <span style={{ color: '#378ADD', cursor: 'pointer', fontWeight: 600, textDecoration: 'none' }}>
              สมัครสมาชิก
            </span>
          </Link>
        </p>

      </div>
    </div>
  );
}