import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // มี package-lock.json ที่ /Users/nannapat ด้วย — ระบุ root ไม่ให้ Turbopack เดาผิดจน next/font หา module ไม่เจอ
  turbopack: { root: __dirname },
  // [Claude] อนุญาตโหลด image จาก localhost:8080 (backend) และ unpkg.com (Leaflet icons)
  images: {
    remotePatterns: [
      { protocol: 'http', hostname: 'localhost', port: '8080' },
      { protocol: 'https', hostname: 'unpkg.com' },
    ],
  },
};

export default nextConfig;
