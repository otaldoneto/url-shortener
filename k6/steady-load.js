import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080';

export const options = {
    scenarios: {
        normal_usage: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 5,
        },
    },
    thresholds: {
        http_req_failed: ['rate==0'],
        http_req_duration: ['p(95)<200'],
    },
};

export default function () {
    const payload = JSON.stringify({ url: `https://example.com/${__ITER}` });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const created = http.post(`${BASE_URL}/api/links`, payload, params);
    check(created, { 'shorten succeeds (201)': (r) => r.status === 201 });

    if (created.status === 201) {
        const code = created.json('code');
        const redirected = http.get(`${BASE_URL}/${code}`, { redirects: 0 });
        check(redirected, { 'redirect succeeds (302)': (r) => r.status === 302 });
    }
}
