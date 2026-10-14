"use client";
import { useState } from "react";
import Link from 'next/link';
import toast from 'react-hot-toast';
import { register } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import Icon from '@/app/components/Icon';
import PasswordField, { PasswordRules } from '@/app/components/PasswordField';
import { useRouter } from 'next/navigation';

export default function RegisterPage() {
  const router = useRouter();

  const [username, setUsername]                 = useState("");
  const [email, setEmail]                       = useState("");
  const [password, setPassword]                 = useState("");
  const [confirmPassword, setConfirmPassword]   = useState("");
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
    if (password.length < 8 || (!/\d/.test(password) && !/[^a-zA-Z0-9]/.test(password))) {
      setError("รหัสผ่านต้องมีความยาวอย่างน้อย 8 ตัวอักษร และต้องมีตัวเลขหรือสัญลักษณ์");
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

  return (
    <>
      <h1>สร้างบัญชีใหม่</h1>
      <p>สมัครครั้งเดียว สั่งขนมได้ทุกวัน</p>
      <form onSubmit={handleConfirm}>
        <div className="field">
          <label htmlFor="reg-user">ชื่อผู้ใช้</label>
          <div className="control">
            <Icon name="user" size="sm" />
            <input id="reg-user" type="text" placeholder="ชื่อที่ใช้แสดงในระบบ" value={username}
              onChange={e => setUsername(e.target.value)} disabled={isLoading} />
          </div>
        </div>
        <div className="field">
          <label htmlFor="reg-email">อีเมล</label>
          <div className="control">
            <Icon name="mail" size="sm" />
            <input id="reg-email" type="email" placeholder="you@email.com" value={email}
              onChange={e => setEmail(e.target.value)} disabled={isLoading} />
          </div>
        </div>
        <PasswordField id="reg-pw" label="รหัสผ่าน" placeholder="อย่างน้อย 8 ตัวอักษร"
          value={password} onChange={e => setPassword(e.target.value)} disabled={isLoading} />
        <PasswordRules password={password} />
        <PasswordField id="reg-pw2" label="ยืนยันรหัสผ่าน" placeholder="พิมพ์รหัสผ่านอีกครั้ง"
          value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} disabled={isLoading} />

        {error && <div className="alert-error" role="alert">{error}</div>}

        <button type="submit" className="btn btn-primary btn-block" disabled={isLoading}>
          {isLoading ? <><Spinner size={16} /> กำลังสมัคร...</> : 'สมัครสมาชิก'}
        </button>
      </form>
      <p className="foot">มีบัญชีอยู่แล้ว? <Link className="link" href="/login">เข้าสู่ระบบ</Link></p>
    </>
  );
}
