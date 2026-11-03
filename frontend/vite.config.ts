import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: { proxy: { '/api': loadEnv(mode, '.', 'API_').API_PROXY_TARGET || 'http://localhost:8080' } },
}));
