import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8001';
const USER = __ENV.USER_ID || 'user_member';

export default function () {
  const res = http.get(`${BASE}/api/v1/projects/proj_abc/board`, {
    headers: { 'X-User-Id': USER },
  });
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has columns': (r) => r.json('columns') !== undefined,
  });
  sleep(0.5);
}
