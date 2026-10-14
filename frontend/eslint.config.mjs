import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import globals from "globals";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // console ในเบราว์เซอร์ใครกด F12 ก็เห็น — ห้าม console.log ค้างไว้ (error/warn ยังใช้ได้)
  {
    files: ["app/**", "lib/**", "services/**"],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: {
      "no-console": ["error", { allow: ["error", "warn"] }],
      // เรียกฟังก์ชัน/ตัวแปรที่ไม่มีอยู่จริง = กดแล้ว crash — ต้องเจอตอน lint ไม่ใช่ตอนผู้ใช้กด
      "no-undef": "error",
    },
  },
  // รูปทุกรูปในแอปมาจาก backend (/uploads) หรือเป็น blob preview ของไฟล์ที่เพิ่งเลือก
  // next/image จะให้ Next server ไปดึงรูปจาก backend เอง — ใน docker ที่อยู่ backend ฝั่ง server
  // ไม่ใช่ localhost:8080 รูปจะพัง และ blob: URL optimize ไม่ได้อยู่แล้ว จึงใช้ <img> ตรง ๆ
  {
    files: ["app/**"],
    rules: { "@next/next/no-img-element": "off" },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
