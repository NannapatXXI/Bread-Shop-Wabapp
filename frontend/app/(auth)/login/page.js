"use client";
import Link from 'next/link';
import { useState } from "react";
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { login } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import Icon from '@/app/components/Icon';
import PasswordField from '@/app/components/PasswordField';
import api from "@/lib/api";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
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
    <>
      <h1>ยินดีต้อนรับกลับมา</h1>
      <p>เข้าสู่ระบบเพื่อสั่งขนมและติดตามออเดอร์</p>
      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="login-id">ชื่อผู้ใช้หรืออีเมล</label>
          <div className="control">
            <Icon name="user" size="sm" />
            <input id="login-id" type="text" placeholder="username หรือ you@email.com"
              value={email} onChange={(e) => setEmail(e.target.value)} disabled={isLoading} />
          </div>
        </div>

        <PasswordField id="login-pw" label="รหัสผ่าน" value={password}
          onChange={(e) => setPassword(e.target.value)} disabled={isLoading} />

        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Link className="link" href="/forgot-password">ลืมรหัสผ่าน?</Link>
        </div>

        {error && <div className="alert-error" role="alert">{error}</div>}

        <button type="submit" className="btn btn-primary btn-block" disabled={isLoading}>
          {isLoading ? <><Spinner size={16} /> กำลังเข้าสู่ระบบ...</> : 'เข้าสู่ระบบ'}
        </button>
        <div className="divider">หรือ</div>
        <button type="button" className="btn btn-outline btn-block" onClick={handleGoogleLogin}>
          <Icon name="google" />เข้าสู่ระบบด้วย Google
        </button>
      </form>
      <p className="foot">ยังไม่มีบัญชี? <Link className="link" href="/register">สมัครสมาชิก</Link></p>
    </>
  );
}
