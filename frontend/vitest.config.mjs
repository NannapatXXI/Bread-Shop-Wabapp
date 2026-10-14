import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [react({ include: /\.(js|jsx)$/ })],
  // ไฟล์ในโปรเจกต์เป็น .js ที่มี JSX
  esbuild: { loader: 'jsx', include: /\.(js|jsx)$/, exclude: [] },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./test/setup.js'],
    include: ['test/**/*.test.{js,jsx}'],
    env: { NEXT_PUBLIC_API_URL: 'http://api.test' },
    restoreMocks: true,
  },
});
