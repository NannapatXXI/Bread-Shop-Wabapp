"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import api from "@/lib/api";

const AuthContext = createContext(null);

const PUBLIC_PATHS = [
  "/", 
  "/login",
  "/register",
  "/forgot-password",
  "/verify-email",
  "/reset-password",
];

export const AuthProvider = ({ children }) => {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (PUBLIC_PATHS.includes(pathname)) {
      setLoading(false);
      return;
    }

    api.get("/api/v1/users/me")
      .then(res => setUser(res.data))
      .catch(() => {
        setUser(null);
        router.push("/login");
      })
      .finally(() => setLoading(false));
  }, [pathname, router]);
  
  const logout = async () => {
    try {
      await api.post("/api/v1/auth/logout");
    } catch (err) {
      console.error("logout error", err);
    } finally {
      setUser(null);
      router.push("/login");
    }
  };

  /** หลังแก้ข้อมูลตัวเอง (เช่น username) — ทุกที่ที่แสดง user (header, หน้าโปรไฟล์) เห็นค่าใหม่ทันที */
  const updateUser = useCallback((patch) => {
    setUser(prev => (prev ? { ...prev, ...patch } : prev));
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, logout, updateUser }}>
      { children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
