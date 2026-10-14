"use client";

import { createContext, useContext, useEffect, useState } from "react";
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

    api.get("/api/v1/auth/me")
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

  return (
    <AuthContext.Provider value={{ user, loading , logout}}>
      { children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
