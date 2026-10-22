import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// 개발 중 프록시가 넘겨줄 백엔드 주소. 8080을 다른 프로그램(예: Jenkins)이 쓰면 BACKEND_URL로 바꾼다.
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080';

// Vite 설정: 개발 서버(5173)와 빌드(dist/) 방법을 정한다.
export default defineConfig({
  plugins: [
    react(),       // JSX를 브라우저가 읽는 JS로 바꾸고, 저장하면 화면이 바로 바뀌게(HMR) 한다
    tailwindcss(), // Tailwind 4: tailwind.config.js 없이 플러그인 + CSS의 @import "tailwindcss"로 동작
  ],
  server: {
    port: 5173,
    // 프록시: 브라우저는 5173에만 요청하고, Vite가 /api·/h를 백엔드(8080)로 대신 넘겨준다.
    // 브라우저 입장에서는 같은 주소라 CORS 설정이 필요 없다. 운영에서는 Nginx가 같은 역할을 한다.
    proxy: {
      '/api': backendUrl,
      '/h': backendUrl,
    },
  },
});
