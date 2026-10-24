import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development, /v1 calls are proxied to the Spring Boot API on :8080,
// so the browser sees one origin and no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/v1': 'http://localhost:8080',
    },
  },
});
