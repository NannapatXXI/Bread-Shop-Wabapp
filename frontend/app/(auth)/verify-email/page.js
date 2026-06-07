"use client";
import Link from 'next/link';

import {useState,   // เก็บ state (ข้อมูลที่เปลี่ยนแปลงได้)
    useRef,     // เก็บ reference (อ้างอิง DOM element)
    useEffect,   // ทำงานหลัง render
    Suspense
 } from "react";

import styles from "./page.module.css";
import { useSearchParams } from "next/navigation";
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast'; // (เราจะใช้ toast แจ้งเตือน)
import { verifyOTP } from "@/services/auth.service";
import { sendOTPEmail } from "@/services/auth.service";

function VerificationForm() {
  const [error, setError] = useState(""); 
  const [isLoading, setIsLoading] = useState(false); // 1. เพิ่ม State สำหรับ Loading
  const router = useRouter();
  const [otp, setOtp] = useState(Array(6).fill(''));
  const inputRefs = useRef([]);
  const length = 6; // ความยาวรหัส OTP
  const [token, setToken] = useState(null);
  const searchParams = useSearchParams();

  
 const email = searchParams.get("email");
  
    useEffect(() => {
        const t = searchParams.get("token");
        setToken(t);
    }, [searchParams]);



  const handleSubmitEmail = async () => {
    try {
        const payload = {
            token : token,
            otp: otp.join("")
        }
        const res = await verifyOTP(payload)
        router.push(`/reset-password?token=${res.data.token}`);
        
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
  



  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      const newOtp = [...otp];
      
      if (otp[index]) {
        // Clear current box
        newOtp[index] = '';
        setOtp(newOtp);
      } else if (index > 0) {
        // Move to previous box and clear it
        newOtp[index - 1] = '';
        setOtp(newOtp);
        if (inputRefs.current[index - 1]) {
          inputRefs.current[index - 1].focus();
        }
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      if (inputRefs.current[index - 1]) {
        inputRefs.current[index - 1].focus();
      }
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      if (inputRefs.current[index + 1]) {
        inputRefs.current[index + 1].focus();
      }
    }
  };


  const handleChange = (index, value) => {
    // Only allow numbers
    if (value && !/^\d$/.test(value)) return;

    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);

    // Auto-focus next input
    if (value && index < length - 1) {
      if (inputRefs.current[index + 1]) {
        inputRefs.current[index + 1].focus();
       
      }
    }
   
    if(newOtp.every(digit => digit !== '')) {
        console.log("---------------------");
   
        console.log("OTP complete:", newOtp.join(''));
        
        console.log("---------------------");
      

    }
      
        
  };

  const handlePaste = (e) => {
       e.preventDefault();
       const pastedData = e.clipboardData.getData('text/plain').slice(0, length);
        const pastedArray = pastedData.split('').filter(char => /^\d$/.test(char));
    
       const newOtp = [...otp];
        pastedArray.forEach((char, idx) => {
         if (idx < length) {
            newOtp[idx] = char;
         }
        });
        setOtp(newOtp);
        
        // Focus on the next empty box or last box
        const nextEmptyIndex = newOtp.findIndex(val => !val);
        const focusIndex = nextEmptyIndex === -1 ? length - 1 : nextEmptyIndex;
        if (inputRefs.current[focusIndex]) {
          inputRefs.current[focusIndex].focus();
        }
        
  };

    const handleFocus = (index) => {
       if (inputRefs.current[index]) {
          inputRefs.current[index].select();
          
        }
 };
    const seadOTP = async() => {
        try {
            const payload = {
                email : email,
                
            }
            const res = await sendOTPEmail(payload);
            const newToken = res.data.token;
           
            router.replace(
                `/verify-email?token=${newToken}&email=${encodeURIComponent(email)}`
              );
            setToken(newToken);
            setOtp(Array(6).fill("")); // ล้างช่อง OTP
            toast.success("ส่ง OTP ใหม่เรียบร้อย");

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
  
  
  
  

  return (
    <div className={styles.controls}>
        <div className={styles.container}>
            <div className={styles.controlsHead} >
                <h1 className="text-xl font-bold text-gray-700 mb-1">Verify Email </h1>
                <h1 className="text-s font-normal text-gray-700 mb-1">Enter your OTP for reset password </h1>
                <h1 className="text-s font-normal text-gray-700 mb-10">Sead to  {email}</h1>
                
            </div>
            


            <form  className={styles.controlsBtn}>
                
           
            
            <div className="flex justify-center gap-3 mt-10">
                <div className="flex justify-center gap-3 mb-1">
                {otp.map((digit, index) => (
                <input
                key={index}
                    ref={el => {
                    inputRefs.current[index] = el;
                    }}
                    type="text"
                    inputMode="numeric"
                maxLength={1}
                    value={digit}
                onChange={e => handleChange(index, e.target.value)}
                    onKeyDown={e => handleKeyDown(index, e)}
                    onPaste={handlePaste}
                    onFocus={() => handleFocus(index)}
                    className="w-14 h-16 text-center text-2xl font-semibold
                            border-2 border-slate-300 rounded-lg
                            focus:border-orange-500 focus:ring-4 focus:ring-orange-200
                            outline-none transition-all duration-200
                            bg-slate-50 text-slate-800
                        hover:border-orange-400"
                    aria-label={`OTP digit ${index + 1}`}
                    />
            ))}
            </div>
          </div>
              
                {/* (แสดง Error) */}
                {error && (
                    <div className="text-red-600 text-sm mt-4 text-center font-medium">
                        {error}
                    </div>
                )}
                
               
                <div className="grid grid-cols-4 gap-0 mt-1 pt-4"> 
                        <div className="col-span-4 flex justify-center mt-1 mb-4">

                                <span
                                    onClick={seadOTP}
                                    className="
                                    text-sm font-normal
                                    inline-flex whitespace-nowrap
                                    text-gray-700 underline cursor-pointer
                                    hover:text-blue-800
                                    "
                                >
                                    Resend OTP
                                </span>
                        </div>


                        <div className="col-span-1 mr-2">
                            <button
                                type="button" 
                                onClick={() => router.push("/login")}
                              
                                className="
                                    bg-[#94a3b8] text-[#475569] py-3 px-4 font-bold rounded-lg 
                                    hover:bg-[#f0f9ff] transition duration-150 w-full 
                                    cursor-pointer border-4 border-[#64748b] hover:border-[#60a5fa]
                                "
                            >
                                Back
                            </button>
                            </div>
                         
                          
                        
                        <div className="col-span-3 mt-1">
                        <button
                            type="button" 
                            // seadOTP
                            onClick={handleSubmitEmail}
                            className="
                                bg-green-600 text-white py-3 px-4 font-bold rounded-lg 
                                transition duration-150 w-full cursor-pointer hover:bg-green-700 
                                col-span-3
                            "
                        >
                            Sead OTP
                        </button>
                      
                        </div>
                       
                    </div>
                    
            </form>
        </div>
    </div>
  );
}

export default function VerifyEmailPage() {
    return (
        <Suspense fallback={<div className="flex justify-center items-center min-h-screen">กำลังโหลด...</div>}>
            <VerificationForm />
        </Suspense>
    );
}