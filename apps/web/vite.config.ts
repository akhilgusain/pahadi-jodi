import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev: browser talks to the gateway through this proxy, so no CORS setup is needed locally.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': process.env.VITE_GATEWAY_URL ?? 'http://localhost:8080' } },
});
