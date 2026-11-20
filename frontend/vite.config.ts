/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // The backend only accepts requests from this exact origin (FRONTEND_HOST), so never silently
  // move to another port when 5173 is busy, fail instead so it's obvious
  server: { port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      // Entry point, type-only and test files have nothing to cover
      exclude: ['src/main.tsx', 'src/Types.tsx', 'src/vite-env.d.ts', 'src/test/**', 'src/**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'text', 'html'],
      thresholds: { lines: 90, statements: 90, functions: 90, branches: 80 },
    },
  },
})
