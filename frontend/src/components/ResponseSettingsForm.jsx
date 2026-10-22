import { useEffect, useState } from 'react';
import { getResponseSettings, saveResponseSettings } from '../api/responseSettings';

// 서버 값 → 폼 상태 (헤더 객체 {이름: 값}를 편집하기 쉬운 줄 배열 [{name, value}]로)
function toForm(settings) {
  return {
    statusCode: settings.statusCode,
    delayMs: settings.delayMs,
    body: settings.body ?? '',
    headers: Object.entries(settings.headers ?? {}).map(([name, value]) => ({ name, value })),
    custom: settings.custom,
  };
}

// 기본 응답 설정 폼 (REQ-RES-001). 저장하면 다음 웹훅부터 이 응답이 나간다.
export default function ResponseSettingsForm({ slug }) {
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null); // { type: 'ok' | 'error', text }

  useEffect(() => {
    getResponseSettings(slug)
      .then((s) => setForm(toForm(s)))
      .catch((e) => setMessage({ type: 'error', text: e.message }));
  }, [slug]);

  if (!form) return message ? <p className="text-sm text-red-600">{message.text}</p> : <p className="text-sm text-slate-400">불러오는 중...</p>;

  const update = (field, value) => setForm({ ...form, [field]: value }); // 한 칸만 바꾼 새 객체 (상태는 직접 고치지 않는다)
  const updateHeader = (index, field, value) =>
    update('headers', form.headers.map((h, i) => (i === index ? { ...h, [field]: value } : h)));

  async function handleSave(event) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const headers = Object.fromEntries(
        form.headers.filter((h) => h.name.trim()).map((h) => [h.name.trim(), h.value]), // 이름이 빈 줄은 버린다
      );
      const saved = await saveResponseSettings(slug, {
        statusCode: Number(form.statusCode),
        delayMs: Number(form.delayMs),
        body: form.body === '' ? null : form.body,
        headers,
      });
      setForm(toForm(saved)); // 서버가 저장한 값으로 다시 채운다
      setMessage({ type: 'ok', text: '저장했어요. 다음 웹훅부터 이 응답이 나가요.' });
    } catch (e) {
      setMessage({ type: 'error', text: e.message }); // 예: "헤더 값에 줄바꿈을 넣을 수 없습니다: X-A"
    } finally {
      setSaving(false);
    }
  }

  const inputClass = 'rounded-md border border-slate-300 px-2 py-1.5 font-mono text-sm';
  return (
    <form onSubmit={handleSave} className="space-y-4 rounded-xl border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-500">{form.custom ? '직접 설정한 응답을 쓰고 있어요.' : '아직 기본 응답(200 {"ok":true})을 쓰고 있어요.'}</p>
      <div className="flex gap-4">
        <label className="flex flex-col text-sm">
          상태 코드
          <input type="number" min={100} max={599} value={form.statusCode} onChange={(e) => update('statusCode', e.target.value)} className={`${inputClass} w-28`} />
        </label>
        <label className="flex flex-col text-sm">
          지연 (ms, 0~5000)
          <input type="number" min={0} max={5000} value={form.delayMs} onChange={(e) => update('delayMs', e.target.value)} className={`${inputClass} w-36`} />
        </label>
      </div>
      <div className="space-y-2">
        <p className="text-sm">헤더</p>
        {form.headers.map((h, i) => (
          <div key={i} className="flex gap-2">
            <input placeholder="이름" value={h.name} onChange={(e) => updateHeader(i, 'name', e.target.value)} className={`${inputClass} w-1/3`} />
            <input placeholder="값" value={h.value} onChange={(e) => updateHeader(i, 'value', e.target.value)} className={`${inputClass} flex-1`} />
            <button type="button" onClick={() => update('headers', form.headers.filter((_, j) => j !== i))} className="px-2 text-slate-400 hover:text-red-600">✕</button>
          </div>
        ))}
        <button type="button" onClick={() => update('headers', [...form.headers, { name: '', value: '' }])} className="text-sm text-blue-600">+ 헤더 추가</button>
      </div>
      <label className="flex flex-col text-sm">
        바디
        <textarea rows={6} value={form.body} onChange={(e) => update('body', e.target.value)} className={inputClass} />
      </label>
      <div className="flex items-center gap-3">
        <button type="submit" disabled={saving} className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50">
          {saving ? '저장 중...' : '저장'}
        </button>
        {message && <span className={`text-sm ${message.type === 'ok' ? 'text-emerald-600' : 'text-red-600'}`}>{message.text}</span>}
      </div>
    </form>
  );
}
