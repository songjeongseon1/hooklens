import client from './client';
import { saveEndpoint } from './tokenStore';

/**
 * 엔드포인트 발급 (POST /api/endpoints, REQ-EP-001).
 * 응답의 anonymousToken은 이때 한 번만 받을 수 있어서 바로 브라우저에 저장한다.
 * @returns {Promise<{slug, url, name, anonymousToken, expiresAt}>}
 */
export async function createEndpoint(name) {
  const endpoint = await client.post('/endpoints', { name: name || null }); // 빈 이름은 null로 (서버도 null로 저장)
  saveEndpoint(endpoint);
  return endpoint;
}
