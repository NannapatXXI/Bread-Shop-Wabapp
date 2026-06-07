"use client";
import { useState, Suspense } from "react";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import toast from 'react-hot-toast';
import Link from 'next/link';
import { useSearchParams } from "next/navigation";
import { resetPassword } from "@/services/auth.service";


import { useRouter } from 'next/navigation';

// (ClassNames เหมือนเดิม)
const containerClasses = "bg-white p-8 rounded-xl shadow-2xl w-full max-w-md";
const controlsClasses = "flex justify-center items-center min-h-screen p-4";

function ResetPasswordForm() {

    // 2. เรียกใช้ useRouter เพื่อสร้างตัวแปร router ที่ขาดไป
    const router = useRouter();

    const searchParams = useSearchParams();
    const token = searchParams.get("token");

    console.log("Token from verify page:", token);
    // (States และ Logic ทั้งหมดเหมือนเดิม)
    const [focusedField, setFocusedField] = useState(null); 
    const [error, setError] = useState(""); 
    const [newPassword, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [isLoading, setIsLoading] = useState(false);


    const infoMessages = {
        username: "ชื่อผู้ใช้ต้องมีความยาว 4-20 ตัวอักษร และไม่มีอักขระพิเศษ",
        email: "กรุณาใช้อีเมลที่ถูกต้อง เช่น yourname@example.com",
        password: "รหัสผ่านต้องมีความยาวอย่างน้อย 6 ตัวอักษร และควรมีตัวเลขหรือสัญลักษณ์",
        confirmPassword: "ยืนยันรหัสผ่านต้องตรงกับช่องรหัสผ่านด้านบน",
    };
    
    const handleConfirm = async(e) => {
       
        e.preventDefault(); 
        setError(""); 
        console.log("Submitting registration:", { token, newPassword });
        // (Validation Logic ทั้งหมดเหมือนเดิม)
        if ( !newPassword || !confirmPassword) {
            setError("กรุณากรอกข้อมูลให้ครบทุกช่อง");
            return;
        }
        const hasNumberOrSymbol = /\d/.test(newPassword) || /[^a-zA-Z0-9]/.test(newPassword);
        if (newPassword.length < 6 || !hasNumberOrSymbol) {
            setError(infoMessages.password);
            return;
        }
        if (newPassword !== confirmPassword) {
            setError(infoMessages.confirmPassword);
            return;
        }

        setIsLoading(true);
        
        try {
            
            const payload = {
                token :token ,
                newPassword: newPassword,
              };
            await resetPassword(payload);
           
              toast.success('เปลี่ยนรหัสผ่านแล้ว!!!  กำลังกลับไปหน้า Login...');
              setTimeout(() => {
                router.push('/login');
              }, 1500);

        
          } catch (err) {
            
              const message =
              err?.response?.data?.message ??
              err?.message ??
              "เกิดข้อผิดพลาดในระบบ";
          
            setError(message);
           
          } finally {
            setIsLoading(false);
          }
    };

    const FocusInfo = ({ fieldName }) => {
        return focusedField === fieldName && (
            <p className="text-xs !text-indigo-600 mt-1 font-medium">
                {infoMessages[fieldName]}
            </p>
        );
    };

    const inputFocusProps = (fieldName) => ({
        onFocus: () => setFocusedField(fieldName),
        onBlur: () => setFocusedField(null),
    });


    return (
        <div className={controlsClasses}>
            <div className={containerClasses}>
                <div className="mb-6 text-center">
                    <h1 className="text-2xl font-extrabold text-gray-900 mb-1">Reset Password</h1>
                </div>
                
                <form onSubmit={handleConfirm} className="space-y-4">
                    {/* (Password Input) */}
                    <div>
                        <p className="text-xs font-medium text-gray-700 mb-1 mt-4">Password</p>
                        <div className="relative">
                            <input
                                type={showPassword ? "text" : "password"}
                                placeholder="••••••••"
                                value={newPassword}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                                {...inputFocusProps('password')} 
                                className="w-full py-2.5 px-4 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition duration-150 pr-10"
                            />
                            
                            <button
                                type="button" 
                                onClick={() => setShowPassword(!showPassword)}
                                className="absolute inset-y-0 right-0 flex items-center justify-center h-full w-10 text-gray-500 hover:text-gray-700"
                                aria-label={showPassword ? "ซ่อนรหัสผ่าน" : "แสดงรหัสผ่าน"}
                            >
                                {showPassword ? <FaEyeSlash /> : <FaEye />}
                            </button>
                        
                        </div> 
                        <FocusInfo fieldName="password" />
                    </div>


                    {/* (Confirm Password Input) */}
                    <div>
                        <p className="text-xs font-medium text-gray-700 mb-1 mt-4">Confirm password</p>
                        <div className="relative">
                            <input
                                type={showConfirmPassword ? "text" : "password"}
                                placeholder="••••••••"
                                value={confirmPassword} 
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                required
                                {...inputFocusProps('confirmPassword')} 
                                className="w-full py-2.5 px-4 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition duration-150 pr-10"
                            />
                            
                            <button
                                type="button"
                                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                className="absolute inset-y-0 right-0 flex items-center justify-center h-full w-10 text-gray-500 hover:text-gray-700"
                                aria-label={showConfirmPassword ? "ซ่อนรหัสผ่าน" : "แสดงรหัสผ่าน"}
                            >
                                {showConfirmPassword ? <FaEyeSlash /> : <FaEye />}
                            </button>
                        
                        </div>
                        <FocusInfo fieldName="confirmPassword" />
                    </div>


                    {/* (Error Message) */}
                    {error && (
                        <div className="text-red-600 text-sm mt-4 text-center font-medium">
                            {error}
                        </div>
                    )}
                    
                    {/* (Buttons) */}
                    <div className="grid grid-cols-4 gap-0 mt-5 pt-4"> 
                        <a href="/login" className="col-span-1 mr-2"> 
                            <button
                                type="button" 
                                className="
                                    bg-[#94a3b8] text-[#475569] py-3 px-4 font-bold rounded-lg 
                                    hover:bg-[#f0f9ff] transition duration-150 w-full 
                                    cursor-pointer border-4 border-[#64748b] hover:border-[#60a5fa]
                                "
                            >
                                Back
                            </button>
                        </a>
                        <button
                            type="submit" 
                            className="
                                bg-green-600 text-white py-3 px-4 font-bold rounded-lg 
                                transition duration-150 w-full cursor-pointer hover:bg-green-700 
                                col-span-3
                            "
                        >
                            Confirm
                        </button>
                    </div>
                </form>
                

            </div>
        </div>
    );
}

export default function ResetPasswordPage() {
    return (
        <Suspense fallback={<div className="flex justify-center items-center min-h-screen">กำลังโหลด...</div>}>
            <ResetPasswordForm />
        </Suspense>
    );
}