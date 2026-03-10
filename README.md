# Sentinel API Gateway

A production-style API gateway built with Spring Boot 3.4, featuring Redis-backed rate limiting, two authentication mechanisms, and API key caching with measurable performance results.

**Live URL:** https://sentinel-api-gateway-production.up.railway.app
> Deployment is paused to save credits and can be redeployed in approximately 3 minutes on request.

---

## Features

- **Redis sliding window rate limiting** with atomic Lua script execution and automatic in-memory fallback
- **Two authentication paths**: JWT for user sessions, API key authentication for programmatic access
- **API key caching** via SHA-256 hashing to eliminate BCrypt verification on cache hits
- **Observability**: Prometheus metrics and Grafana dashboards (local), Spring Boot Actuator (live)
- **Deployed** on Railway with PostgreSQL and Redis

---

## Performance

Redis API key caching reduced average latency by 94% and increased throughput by 24x under stress conditions.

| Test | VUs | Avg Latency | P95 Latency | Throughput | Failure Rate |
|---|---|---|---|---|---|
| Pre-cache baseline | 20 | 61.59ms | 68.85ms | 42.46 req/s | 24.25% (429) |
| Pre-cache stress | 50 | 439.4ms | 761.29ms | 111.94 req/s | 16.07% (401) |
| Post-cache baseline | 20 | 3.7ms | 4.67ms | 47.09 req/s | 30.74% (429) |
| Post-cache stress | 50 | 15.15ms | 27.34ms | 2,744 req/s | 1.80% (401) |

### On Failure Rates

The higher failure rate in the post-cache baseline (30.74% vs 24.25%) is expected behavior, not a regression. Before caching, BCrypt verification added approximately 60ms per request, which limited how many requests could complete within the 60-second rate limit window. After caching, requests resolve in ~2ms, so the same number of virtual users exhaust the 100 req/min limit faster and produce more 429 responses. The rate limiter is functioning correctly.

Pre-cache stress failures are 401s caused by BCrypt thread contention under 50 concurrent users, not rate limit breaches.

See [LOAD_TESTING.md](./LOAD_TESTING.md) for full methodology and results.

---

## Architecture

```
Request
  │
  ▼
RateLimiterFilter          ← Sliding window check (Redis Lua script)
  │                           Falls back to in-memory if Redis is unavailable
  ▼
JwtAuthFilter              ← Validates Bearer token; skips if X-API-KEY present
  │
  ▼
ApiKeyAuthFilter           ← SHA-256 lookup in Redis cache (5-min TTL)
  │                           On cache miss: BCrypt verify → populate cache
  ▼
Controller / Business Logic
```

### Design Decisions

**Sliding window over fixed window** — A fixed window allows burst attacks at window boundaries, where a client can send twice the allowed limit in rapid succession by straddling two windows. The sliding window tracks request timestamps in a Redis sorted set, giving accurate per-key counts at any point in time.

**Lua script for atomicity** — The rate limit check and increment must happen atomically. Without a Lua script, a race condition between `ZCARD` and `ZADD` allows multiple concurrent requests to pass when the limit is exactly full. The Lua script executes as a single Redis operation.

**SHA-256 as cache key** — Storing the raw API key in Redis would expose valid credentials to anyone with Redis access. SHA-256 is a one-way hash; the cache maps `hash(rawKey) → userEmail`, so even full Redis exposure reveals nothing replayable.

**In-memory fallback** — Redis is a network dependency. If it becomes unavailable, the gateway continues rate limiting per-instance using `ConcurrentHashMap` and `AtomicInteger`. The trade-off is that per-instance limits do not coordinate across replicas, but availability is maintained.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.4 |
| Security | Spring Security (stateless) |
| Rate Limiting | Redis 7.2 (sorted sets + Lua), in-memory fallback |
| Session Auth | JWT via jjwt 0.12.6 |
| Programmatic Auth | API key + BCrypt + Redis cache |
| Database | PostgreSQL 16 + Flyway migrations |
| Observability | Micrometer, Prometheus, Grafana |
| Load Testing | k6 |
| Deployment | Railway |

---

## Project Structure

```
src/main/java/com/sentinel/apigateway/
├── config/
│   ├── SecurityConfig.java           # Filter chain ordering
│   └── PasswordEncoderConfig.java    # BCrypt bean
├── controller/
│   ├── AuthController.java           # Register, login
│   ├── ApiKeyController.java         # Key generation
│   ├── HealthController.java
│   └── DummyEndpoint.java            # Load test target
├── filter/
│   └── RateLimiterFilter.java        # First in chain
├── security/
│   ├── JwtAuthFilter.java
│   └── ApiKeyAuthFilter.java         # Cache-first auth
├── repository/
│   ├── RateLimitRepository.java      # Interface
│   ├── RedisRateLimitRepository.java # Primary (@Primary)
│   └── InMemoryRateLimitRepository.java # Fallback
├── service/
│   ├── RateLimiterService.java
│   ├── UserService.java
│   └── JwtUtil.java
├── entity/          # User, ApiKey (JPA)
├── dto/             # Request/Response records
└── exception/       # GlobalExceptionHandler, custom exceptions

src/main/resources/
├── scripts/
│   └── sliding_window.lua            # Atomic rate limit script
└── db/migration/
    └── V1__init_schema.sql
```

---

## API Reference

All endpoints except `/health` and `/api/auth/**` require authentication.

### Authentication

**JWT:** Include `Authorization: Bearer <token>` in the request header.

**API Key:** Include `X-API-KEY: <key>` in the request header.

---

### `POST /api/auth/register`

Register a new user.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response `201`:**
```json
{
  "id": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

---

### `POST /api/auth/login`

Authenticate and receive a JWT.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response `200`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

### `POST /api/keys`

Generate an API key. Requires JWT authentication.

**Headers:** `Authorization: Bearer <token>`

**Response `200`:** Raw API key string. This value is shown once and cannot be retrieved again.

---

### `GET /health`

Basic health check. No authentication required.

**Response `200`:** `Sentinel is active. System Status: GREEN.`

---

### `GET /actuator/health`

Detailed health status including database and Redis connectivity. No authentication required.

---

### `GET /api/dummy`

Test endpoint used for load testing. Requires authentication.

**Response `200`:** `true`

---

## Rate Limiting

- **Algorithm:** Sliding window
- **Window:** 60 seconds
- **Limit:** 100 requests per API key per window
- **Primary storage:** Redis sorted set, one per API key
- **Fallback:** In-memory when Redis is unavailable
- **Response on breach:** `429 Too Many Requests` with `Retry-After: 60` header

```json
{
  "error": "Rate limit exceeded",
  "status": 429,
  "message": "You have exhausted your request quota. Please try again later."
}
```

---

## Local Setup

### Prerequisites

- Docker and Docker Compose
- Java 21
- k6 (optional, for load testing)

### Run with Docker Compose

```bash
git clone https://github.com/lithincg/sentinel-api-gateway
cd sentinel-api-gateway
docker compose up --build
```

The gateway starts on `http://localhost:8080`.

| Service | URL |
|---|---|
| Gateway | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

### Example Usage

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
# Response: {"token":"eyJ..."}

# Generate API key
curl -X POST http://localhost:8080/api/keys \
  -H "Authorization: Bearer eyJ..."
# Response: 54139b7b96764f5b91b6359a0b30f283

# Make an authenticated request
curl http://localhost:8080/api/dummy \
  -H "X-API-KEY: 54139b7b96764f5b91b6359a0b30f283"
```

---

## Observability

Prometheus metrics are exposed at `/actuator/prometheus`. The Docker Compose stack includes Prometheus (scraping every 10 seconds) and Grafana configured to connect to it.

Grafana panels include JVM heap usage, HTTP request rate by endpoint, P95 and average latency, and rate limit rejections.

Prometheus and Grafana are local only and are not included in the Railway deployment.

---

## Git History

The optimization journey is preserved across branches:

- `pre-redis-stressTest` — baseline: no caching, BCrypt runs on every request
- `main` — optimized: Redis API key cache with 5-minute TTL

Both branches can be checked out to reproduce the before/after load test results independently.

---

## Deployment

Deployed on Railway with Spring Boot, PostgreSQL, and Redis as separate services.

`JWT_SECRETKEY` is set as a Railway environment variable and is not committed to source. Database and Redis connection details are injected by Railway at runtime.

---

## Tests

```bash
./mvnw test
```

- **Unit tests:** `RateLimiterTest` — concurrent access correctness, per-user isolation
- **Mock tests:** `UserRepositoryTest`, `ApiKeyRepositoryTest`
- **Integration test:** `AuthControllerIT` — Testcontainers with a real PostgreSQL instance
