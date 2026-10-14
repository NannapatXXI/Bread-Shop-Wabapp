"use client";
import { useState, Suspense } from "react";
import Link from 'next/link';
import toast from 'react-hot-toast';
import { useSearchParams, useRouter } from "next/navigation";
import { resetPassword } from "@/services/auth.service";
import Spinner from '@/app/components/Spinner';
import PasswordField, { PasswordRules } from '@/app/components/PasswordField';

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  const [newPassword, setNewPassword]         = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError]                     = useState("");
  const [isLoading, setIsLoading]             = useState(false);

  const handleConfirm = async (e) => {
    e.preventDefault();
    setError("");

    if (!newPassword || !confirmPassword) { setError("กรุณากรอกข้อมูลให้ครบทุกช่อง"); return; }
    if (newPassword.length < 8 || (!/\d/.test(newPassword) && !/[^a-zA-Z0-9]/.test(newPassword))) {
      setError("รหัสผ่านต้องมีความยาวอย่างน้อย 8 ตัวอักษร และต้องมีตัวเลขหรือสัญลักษณ์");
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

  return (
    <>
      <h1>ตั้งรหัสผ่านใหม่</h1>
      <p>กรอกรหัสผ่านใหม่ที่ต้องการใช้</p>
      <form onSubmit={handleConfirm}>
        <PasswordField id="rp-pw" label="รหัสผ่านใหม่" placeholder="อย่างน้อย 8 ตัวอักษร"
          value={newPassword} onChange={e => setNewPassword(e.target.value)} disabled={isLoading} />
        <PasswordRules password={newPassword} />
        <PasswordField id="rp-pw2" label="ยืนยันรหัสผ่านใหม่" placeholder="พิมพ์รหัสผ่านอีกครั้ง"
          value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} disabled={isLoading} />

        {error && <div className="alert-error" role="alert">{error}</div>}

        <button type="submit" className="btn btn-primary btn-block" disabled={isLoading}>
          {isLoading ? <><Spinner size={16} /> กำลังบันทึก...</> : 'บันทึกรหัสผ่านใหม่'}
        </button>
      </form>
      <p className="foot"><Link className="link" href="/login">กลับไปหน้าเข้าสู่ระบบ</Link></p>
    </>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<p className="muted">กำลังโหลด...</p>}>
      <ResetPasswordForm />
    </Suspense>
  );
}
