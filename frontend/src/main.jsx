import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App.jsx';
import './index.css';

// 앱의 시작점: index.html의 <div id="root">에 React 화면을 붙인다.
createRoot(document.getElementById('root')).render(
  // StrictMode: 개발 중에만 컴포넌트를 두 번 실행해 실수(정리 안 한 연결 등)를 일찍 드러낸다. 운영 빌드에는 영향 없음
  <StrictMode>
    {/* BrowserRouter: 주소창의 경로(/, /e/abc)에 따라 다른 화면을 보여 준다 (페이지 새로고침 없이) */}
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
