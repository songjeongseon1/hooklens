import { useState } from 'react';

// 글자를 클립보드에 복사하고, 1.5초 동안 "복사됨"을 보여 주는 버튼.
export default function CopyButton({ text }) {
  const [copied, setCopied] = useState(false); // 방금 복사했는지 (버튼 문구용)

  async function handleClick() {
    await navigator.clipboard.writeText(text); // 브라우저 클립보드 API (localhost·https에서만 동작)
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);  // 1.5초 뒤 원래 문구로
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700"
    >
      {copied ? '복사됨 ✓' : '복사'}
    </button>
  );
}
