# Load Testing — Sentinel API Gateway

This document covers the methodology, configuration, and results for all four load tests run against Sentinel API Gateway. The tests demonstrate a before/after optimization story: API key caching using Redis eliminated repeated BCrypt verification, producing a 94% latency reduction and 24x throughput increase under stress.

---

## Overview

| Test | Branch | Cache | VUs | Sleep | Avg Latency | P95 | Throughput | Failure Rate |
|---|---|---|---|---|---|---|---|---|
| Pre-cache baseline | `pre-redis-stressTest` | None | 20 | 0.4s | 61.59ms | 68.85ms | 42.46 req/s | 24.25% (429) |
| Pre-cache stress | `pre-redis-stressTest` | None | 50 | 0s | 439.4ms | 761.29ms | 111.94 req/s | 16.07% (401) |
| Post-cache baseline | `main` | Redis (5-min TTL) | 20 | 0.4s | 3.7ms | 4.67ms | 47.09 req/s | 30.74% (429) |
| Post-cache stress | `main` | Redis (5-min TTL) | 50 | 0s | 15.15ms | 27.34ms | 2,744 req/s | 1.80% (401) |

---

## Setup

### Prerequisites

- k6 installed ([k6.io/docs/get-started/installation](https://k6.io/docs/get-started/installation))
- Gateway running locally via Docker Compose
- 20 or 50 API keys pre-generated and inserted into the k6 script

### Pre-test Checklist

1. Confirm `maxRequests = 100` in `RateLimiterService.java` (always revert after stress tests)
2. Confirm Redis is running: `docker ps | grep sentinel-redis`
3. For pre-cache tests: checkout `pre-redis-stressTest` branch
4. For post-cache tests: stay on `main`
5. For stress tests: restart containers fresh to avoid warm cache interference (`docker compose down && docker compose up --build`)

---

## Test Configuration

### Baseline Script (20 VUs, 0.4s sleep)

```javascript
import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    stages: [
        { duration: "30s", target: 20 },
        { duration: "2m",  target: 20 },
        { duration: "10s", target: 0  },
    ],
};

const keys = [ /* 20 API keys */ ];

export default function() {
    const rawKey = keys[__VU - 1];
    http.get("http://localhost:8080/api/dummy", {
        headers: { "X-API-KEY": rawKey }
    });
    sleep(0.4);
}
```

### Stress Script (50 VUs, no sleep)

```javascript
import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    stages: [
        { duration: "10s", target: 25 },
        { duration: "10s", target: 50 },
        { duration: "10m", target: 50 },
    ],
};

const keys = [ /* 50 API keys */ ];

export default function() {
    const rawKey = keys[__VU - 1];
    http.get("http://localhost:8080/api/dummy", {
        headers: { "X-API-KEY": rawKey }
    });
    sleep(0.0);
}
```

Run with:
```bash
k6 run load-test.js
```

---

## Test Results — Detailed

### Test 1: Pre-cache Baseline

**Branch:** `pre-redis-stressTest` | **VUs:** 20 | **Sleep:** 0.4s | **Duration:** ~2.5 min

**What happens on every request:**
1. RateLimiterFilter: Redis ZCARD + ZADD via Lua script
2. ApiKeyAuthFilter: prefix lookup in PostgreSQL, then BCrypt.matches() against each candidate (~60ms)
3. Controller: returns `true`

**Results:**
- Avg latency: **61.59ms** — BCrypt dominates
- P95: **68.85ms**
- Throughput: **42.46 req/s**
- Failure rate: **24.25%** — all 429s, rate limit correctly triggered

**Grafana:** 200 responses average ~59ms. 429 responses resolve in under 1ms since they are rejected by the rate limiter before BCrypt runs.

**Pre-cache baseline — Grafana**

<img width="1512" height="735" alt="pre-cache-baselineTest-grafana" src="https://github.com/user-attachments/assets/8e15b3a6-0b42-40bc-84f4-9a37a2e47120" />


**Pre-cache baseline — k6**

<img width="1548" height="931" alt="pre-cache-baselineTest-k6" src="https://github.com/user-attachments/assets/d47fca57-35ea-427d-b8eb-9ad175b469bd" />


---

### Test 2: Pre-cache Stress

**Branch:** `pre-redis-stressTest` | **VUs:** 50 | **Sleep:** 0s | **Duration:** ~10 min

**Conditions:** Fresh container restart, cold JVM. No prior warmup — the most production-realistic baseline.

**What happens under load:**
- 50 concurrent VUs all invoking BCrypt simultaneously on every request
- BCrypt is CPU-bound; the thread pool saturates under this concurrency
- Some requests cannot complete authentication in time, returning 401s

**Results:**
- Avg latency: **439.4ms** — approximately 7x worse than baseline due to thread contention
- P95: **761.29ms**
- Throughput: **111.94 req/s**
- Failure rate: **16.07%** — predominantly **401s** (auth failures from contention), not 429s

**Grafana:** Response time starts near 650ms at the beginning of the test and gradually drops to ~325ms over 10 minutes as the JIT compiler warms up the BCrypt code path. This warmup curve is unique to this test — all other tests were run after the JVM was already warm.

**Pre-cache stress — Grafana**

<img width="1507" height="722" alt="pre-cache-stressTest-grafana" src="https://github.com/user-attachments/assets/56f98008-f9f8-48ba-8dd6-b4474f93c43f" />

**Pre-cache stress — k6**

<img width="1669" height="930" alt="pre-cache-stressTest-k6" src="https://github.com/user-attachments/assets/fae4a70a-a82d-48e3-8a80-ceafd01af069" />

---

### Test 3: Post-cache Baseline

**Branch:** `main` | **VUs:** 20 | **Sleep:** 0.4s | **Duration:** ~2.5 min

**What happens on every request (after first request per key):**
1. RateLimiterFilter: Redis Lua script
2. ApiKeyAuthFilter: SHA-256 hash of raw key → Redis GET → cache hit → authentication set directly
3. BCrypt: **never called**

**Results:**
- Avg latency: **3.7ms** — 94% reduction from 61.59ms
- P95: **4.67ms**
- Throughput: **47.09 req/s**
- Failure rate: **30.74%** — all 429s, higher than pre-cache (see explanation below)

**Why the higher failure rate is correct:** BCrypt added ~60ms per request, which unintentionally limited how many requests could complete within the 60-second sliding window. With the cache eliminating that cost, requests resolve in ~2ms, so the same 20 VUs exhaust the 100 req/min quota faster. The rate limiter is working correctly — it is simply being reached more often.

**Grafana:** 200 responses average ~2ms. 429 responses average ~880µs. The separation between the two lines is clear, in contrast to the pre-cache baseline where both converged around 60ms.

**Post-cache baseline — Grafana**

<img width="1511" height="713" alt="post-cache-baselineTest-grafana" src="https://github.com/user-attachments/assets/35f55b4b-b3b3-41d7-9e1a-a7ebf7f3761d" />

**Post-cache baseline — k6**

<img width="1532" height="940" alt="post-cache-baselineTest-k6" src="https://github.com/user-attachments/assets/b55ce680-6aa3-4c99-9980-c43adb1c10cc" />


---

### Test 4: Post-cache Stress

**Branch:** `main` | **VUs:** 50 | **Sleep:** 0s | **Duration:** ~10 min

**Results:**
- Avg latency: **15.15ms**
- P95: **27.34ms**
- Throughput: **2,744 req/s** — 24x improvement over pre-cache stress
- Failure rate: **1.80%** — small number of 401s (see explanation below)

**Why there are any 401s at all:** The test starts with a fresh container, so the Redis cache is empty. All 50 VUs fire simultaneously before any cache entries exist, forcing that first wave of requests through BCrypt concurrently. Under that burst, some authentication attempts fail due to thread contention — the same root cause as the pre-cache stress failures, just limited to the brief cold-start window before the cache is populated. Once each key is cached after its first successful verification, all subsequent requests resolve in ~2ms and no further 401s occur.

**Grafana:** 200 responses hold steady at ~8ms throughout the test. 401 responses average ~148ms, reflecting the cold-start BCrypt contention at the beginning of the run.

**Post-cache stress — Grafana**

<img width="1506" height="752" alt="post-cache-stressTest-grafana" src="https://github.com/user-attachments/assets/0b5ab141-ac62-4821-b444-d33dc45fb254" />

**Post-cache stress — k6**

<img width="1620" height="925" alt="post-cache-stressTest-k6" src="https://github.com/user-attachments/assets/490f2dea-3e49-42a0-b57a-3616ea4be67e" />


---

## What the Cache Actually Does

Without cache, every API key request hits this path:

```
rawKey → prefix lookup (DB) → BCrypt.matches(rawKey, storedHash) → ~60ms
```

With cache, after the first request per key:

```
rawKey → SHA-256(rawKey) → Redis GET(hash) → email string → done → ~2ms
```

**Why SHA-256 and not the raw key as the Redis key?**

The raw API key is a secret. If Redis is compromised, an attacker with `KEYS *` access would see every active API key and could immediately replay them. SHA-256 is a one-way hash — the cache stores `hash → email`, so even full Redis exposure gives the attacker nothing replayable.

**Why 5-minute TTL?**

Long enough to cover realistic request bursts within a session. Short enough that a revoked key is ejected from cache within 5 minutes without requiring active cache invalidation. Immediate invalidation on revoke is a planned feature — the correct implementation would delete the cache entry at the point of revocation.

---

## Reproducing the Tests

```bash
# Tests 1 & 2: Pre-cache
git checkout pre-redis-stressTest
docker compose down && docker compose up --build -d
# Wait ~30s for startup
k6 run load-test.js

# Tests 3 & 4: Post-cache
git checkout main
docker compose down && docker compose up --build -d
k6 run load-test.js
```
