// k6 load test for the warehouse app.
//
// Normal load test:   k6 run -e BASE_URL=http://127.0.0.1:8080 k6/load-test.js
// Breakpoint test:    k6 run -e BASE_URL=http://127.0.0.1:8080 -e PROFILE=breakpoint k6/load-test.js
// Another host/port:  k6 run -e BASE_URL=http://127.0.0.1:8099 k6/load-test.js
//
// Notes:
// - The login in setup() is tagged name:login and is NOT part of any aborting threshold,
//   so one slow first connection can no longer stop the test before the VUs start.
// - Use 127.0.0.1 instead of localhost to avoid IPv6 (::1) fallback delays on Windows.

import http from 'k6/http';
import { check, sleep, fail } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const PROFILE = __ENV.PROFILE || 'load';
const IS_BREAKPOINT = PROFILE === 'breakpoint';

const profiles = {
  // Normal load: up to 25 VUs for 50 s (10 s up, 30 s hold, 10 s down).
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 25 },
      { duration: '30s', target: 25 },
      { duration: '10s', target: 0 },
    ],
    gracefulRampDown: '5s',
    gracefulStop: '30s',
  },

  // Breakpoint: stepped arrival-rate ramp, 10 s ramp + 20 s hold per step.
  breakpoint: {
    executor: 'ramping-arrival-rate',
    startRate: 100,
    timeUnit: '1s',
    preAllocatedVUs: 200,
    maxVUs: 800,
    stages: [
      { duration: '10s', target: 250 },
      { duration: '20s', target: 250 },
      { duration: '10s', target: 500 },
      { duration: '20s', target: 500 },
      { duration: '10s', target: 1000 },
      { duration: '20s', target: 1000 },
      { duration: '10s', target: 1500 },
      { duration: '20s', target: 1500 },
      { duration: '10s', target: 2000 },
      { duration: '20s', target: 2000 },
    ],
  },
};

const thresholds = IS_BREAKPOINT
  ? {
      // Looser limits: the point of a breakpoint test is to find where the app breaks.
      http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '10s' }],
      http_req_duration: ['p(95)<2000'],
      'http_req_duration{name:list}': [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '10s' }],
      'http_req_duration{name:search}': [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '10s' }],
      'http_req_duration{name:receive}': [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '10s' }],
      'http_req_duration{name:pick}': [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '10s' }],
    }
  : {
      http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '5s' }],
      // Global duration is reported but does not abort (it would include the login in setup()).
      http_req_duration: ['p(95)<500'],
      'http_req_duration{name:list}': [{ threshold: 'p(95)<300', abortOnFail: true, delayAbortEval: '5s' }],
      'http_req_duration{name:search}': [{ threshold: 'p(95)<300', abortOnFail: true, delayAbortEval: '5s' }],
      'http_req_duration{name:receive}': [{ threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '5s' }],
      'http_req_duration{name:pick}': [{ threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '5s' }],
      checks: ['rate>0.99'],
    };

export const options = {
  scenarios: { [PROFILE]: profiles[PROFILE] },
  thresholds,
};

export function setup() {
  const res = http.post(
    `${BASE}/api/login`,
    JSON.stringify({ username: 'admin', password: 'admin123' }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'login' },
      timeout: '30s',
    }
  );
  if (res.status !== 200) {
    fail(`Login failed with status ${res.status}. Is the app running at ${BASE}?`);
  }
  return { token: res.json('token') };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` };

  const list = http.get(`${BASE}/api/items`, { headers, tags: { name: 'list' } });
  check(list, { 'list 200': (r) => r.status === 200 });

  const search = http.get(`${BASE}/api/items?q=scanner`, { headers, tags: { name: 'search' } });
  check(search, { 'search 200': (r) => r.status === 200 });

  const receive = http.post(
    `${BASE}/api/items/SKU-1003/receive`,
    JSON.stringify({ quantity: 1 }),
    { headers, tags: { name: 'receive' } }
  );
  check(receive, { 'receive 200': (r) => r.status === 200 });

  const pick = http.post(
    `${BASE}/api/items/SKU-1003/pick`,
    JSON.stringify({ quantity: 1 }),
    { headers, tags: { name: 'pick' } }
  );
  check(pick, { 'pick 200': (r) => r.status === 200 });

  if (!IS_BREAKPOINT) {
    sleep(1);
  }
}