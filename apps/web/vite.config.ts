/// <reference types="vitest/config" />
import path from 'node:path'
import react from '@vitejs/plugin-react'
import { rmSync } from 'node:fs'
import { defineConfig, type Plugin } from 'vite'

/** public/mockServiceWorker.js exists for the offline demo only; never publish it. */
const dropMockWorker = (): Plugin => ({
  name: 'drop-mock-service-worker',
  apply: 'build',
  closeBundle() {
    rmSync(path.resolve(__dirname, 'dist/mockServiceWorker.js'), { force: true })
  },
})

export default defineConfig({
  plugins: [react(), dropMockWorker()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: { port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Form tests type into many fields and lazy chunks transform on first use; 5 s is too tight on a cold run.
    testTimeout: 15_000,
  },
})
