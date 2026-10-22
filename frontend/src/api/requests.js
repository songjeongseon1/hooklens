import client, { authHeaders } from './client';

/**
 * 요청 목록 최신순 (GET /api/endpoints/{slug}/requests, REQ-LOG-002).
 * cursor를 안 주면 첫 페이지, 주면 그 id보다 오래된 것부터.
 * @returns {Promise<{items: Array, nextCursor: number|null}>}
 */
export function getRequests(slug, cursor) {
  return client.get(`/endpoints/${slug}/requests`, {
    headers: authHeaders(slug),
    params: { size: 20, ...(cursor ? { cursor } : {}) }, // ?size=20&cursor=81 (cursor는 있을 때만)
  });
}

/** 요청 상세 — 헤더(객체)·쿼리·바디 포함 (GET /api/endpoints/{slug}/requests/{id}, REQ-LOG-003). */
export function getRequestDetail(slug, requestId) {
  return client.get(`/endpoints/${slug}/requests/${requestId}`, { headers: authHeaders(slug) });
}
