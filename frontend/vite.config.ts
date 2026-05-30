import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/META-INF/resources',
    emptyOutDir: true,
  },
});
