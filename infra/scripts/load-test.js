import http from 'k6/http';
import { check, sleep } from 'k6';

// Existing public share download path. Set BASE_URL and SHARE_TOKEN.
// A general authenticated file upload/download API is not present yet, so this
// targets the currently implemented download route without pretending to test upload.
export const options = {
  scenarios: {
    parallel_downloads: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: __ENV.RAMP_DURATION || '30s', target: Number(__ENV.VUS || 20) },
        { duration: __ENV.DURATION || '1m', target: Number(__ENV.VUS || 20) },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

export default function () {
  const base = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
  const token = __ENV.SHARE_TOKEN;
  if (!token) throw new Error('Set SHARE_TOKEN to a valid share link token');
  const response = http.get(`${base}/s/${encodeURIComponent(token)}/download`, {
    redirects: 0,
    tags: { name: 'share_download' },
  });
  check(response, { 'download returned 200 or 206': (r) => r.status === 200 || r.status === 206 });
  sleep(0.2);
}
