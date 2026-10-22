// 서버가 주는 시간은 ISO-8601 + 오프셋("2026-10-22T10:00:00+09:00")이라 new Date()로 바로 정확히 읽힌다.

/** 목록용: "방금", "3분 전" */
export function timeAgo(iso) {
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 10) return '방금';
  if (seconds < 60) return `${seconds}초 전`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.floor(hours / 24)}일 전`;
}

/** 상세용: "2026. 10. 22. 10:00:00" (서울 시간 기준) */
export function formatDateTime(iso) {
  return new Date(iso).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false });
}
