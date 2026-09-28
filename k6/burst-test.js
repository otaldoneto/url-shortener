import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080';

export const options = {
    scenarios: {
        burst: {
            executor: 'shared-iterations',
            vus: 30,
            iterations: 30,
            maxDuration: '10s',
        },
    },
};

export default function () {
    const payload = JSON.stringify({ url: `https://example.com/burst-${__VU}-${__ITER}` });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const res = http.post(`${BASE_URL}/api/links`, payload, params);
    check(res, {
        'got 201 or 429': (r) => r.status === 201 || r.status === 429,
    });
}
