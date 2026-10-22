import client, { authHeaders } from './client';

/** 지금 응답 설정. 설정 전이면 기본값 + custom: false (GET /api/endpoints/{slug}/response, REQ-RES-001). */
export function getResponseSettings(slug) {
  return client.get(`/endpoints/${slug}/response`, { headers: authHeaders(slug) });
}

/**
 * 응답 설정 저장 — 통째로 바꾼다 (PUT, 멱등).
 * @param {{statusCode: number, headers: Object<string,string>, body: string, delayMs: number}} settings
 */
export function saveResponseSettings(slug, settings) {
  return client.put(`/endpoints/${slug}/response`, settings, { headers: authHeaders(slug) });
}
