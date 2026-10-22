import { Link, Route, Routes } from 'react-router';
import HomePage from './pages/HomePage';
import EndpointPage from './pages/EndpointPage';

// 주소 → 화면 연결표
export default function App() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center px-4 py-3">
          <Link to="/" className="text-lg font-bold">
            🔍 HookLens
          </Link>
          <span className="ml-3 text-sm text-slate-500">웹훅 디버거</span>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/e/:slug" element={<EndpointPage />} />
          <Route path="*" element={<p>없는 페이지예요. <Link to="/" className="text-blue-600">홈으로</Link></p>} />
        </Routes>
      </main>
    </div>
  );
}
