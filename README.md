# DocuMind AI Portal

A multi-tenant, production-shaped RAG (Retrieval-Augmented Generation) platform: upload PDF documents, ask questions about them, and get cited, guardrailed answers grounded only in what was actually retrieved. Built with Spring Boot + Spring AI on the backend and Angular on the frontend.

This isn't a "call an LLM API" demo — it's the surrounding system a real RAG product needs: async ingestion pipeline, tenant-scoped vector retrieval, an eight-stage input/output guardrail chain, JWT auth with refresh rotation, audit logging, and a DevSecOps CI pipeline (CodeQL, dependency scanning, container image scanning).

## Why this exists

Most RAG tutorials stop at "embed a document, retrieve chunks, stuff them in a prompt." This project treats that as the easy 20% and builds out the other 80%: what happens when an upload fails partway through, how a multi-tenant system keeps one organization's documents out of another's answers, what stops a jailbreak attempt or a hallucinated citation before it reaches a user, and how the whole thing gets safely deployed.

## Architecture

```
                                   ┌─────────────┐
                                   │   Angular    │
                                   │   Frontend   │
                                   └──────┬──────┘
                                          │ JWT bearer + refresh-on-401/403
                                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          Spring Boot Backend                         │
│                                                                       │
│  Upload → DocumentUploadService → [validate, reserve quota, store]   │
│              │                                                       │
│              ▼                                                       │
│  IngestionJobScheduler (polling worker, FOR UPDATE SKIP LOCKED)      │
│     EXTRACTING → CHUNKING → JSONL_STAGED → EMBEDDING → READY         │
│     (Tika)      (heading-   (staged to    (Voyage AI    (pgvector    │
│                  aware       object        embeddings)   write)      │
│                  chunker)    storage)                                │
│                                                                       │
│  Ask → ChatOrchestrationService                                      │
│     ├─ input guardrails:  rate limit → prompt injection              │
│     ├─ retrieval:         tenant-scoped pgvector similarity search    │
│     └─ output guardrails: scope refusal → citations → toxicity →     │
│                            groundedness (LLM-as-judge, checked last) │
└─────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
   PostgreSQL+pgvector      MinIO (S3)          ClamAV
```

## Core features

- **Multi-tenant document RAG** — upload a PDF, it's chunked, embedded (Voyage AI), and indexed in pgvector, scoped to your organization (and optionally a single document) at the SQL level, never trusted from client input.
- **Guardrailed chat, not a raw model passthrough** — every turn passes through:
  - *Input:* per-user rate limiting, prompt-injection detection
  - *Output:* scope-refusal (no relevant chunks → refuse rather than hallucinate), citation enforcement (answer must cite retrieved chunks), toxicity filtering, and a groundedness LLM-judge (checked last, since it's the most expensive)
  - Every guardrail failure is flagged for audit review with a severity level, not silently dropped
- **Streaming answers over SSE**, with a documented tradeoff: output guardrails can only run after a stream completes (content's already reached the browser), so violations there are flagged for review instead of blocked — a deliberate, documented decision rather than an oversight.
- **Async ingestion pipeline** — upload returns `202 Accepted` immediately; a `FOR UPDATE SKIP LOCKED` polling worker drives extraction → chunking → embedding in the background, so a slow PDF never blocks the HTTP request, and multiple workers never double-process the same job.
- **JWT auth with rotation** — short-lived access tokens, opaque refresh tokens stored only as a SHA-256 hash (never raw), automatic re-fetch of the user's live role/status on every request rather than trusting a stale JWT claim.
- **Role-based admin** — org admins can list/disable users and change roles; a demoted user is locked out of admin endpoints on their very next request, not after their token happens to expire.
- **Usage analytics & storage quotas** — per-organization storage quota reserved *before* bytes are written (so a quota-exceeding upload never touches object storage), plus an admin usage dashboard.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring AI 1.1, Spring Security, Spring Data JPA |
| LLM / embeddings | Anthropic Claude (chat), Voyage AI (embeddings, via a hand-rolled `EmbeddingModel`) |
| Database | PostgreSQL 16 + pgvector, Flyway migrations |
| Object storage | S3-compatible (MinIO locally, AWS S3 in prod) — raw PDFs + staged JSONL chunks |
| Malware scanning | ClamAV, as an early ingestion-pipeline stage |
| Text extraction | Apache Tika (PDF → Markdown) |
| Frontend | Angular 22 (standalone components, signals), RxJS |
| CI/CD | GitHub Actions: build+test, CodeQL SAST, dependency vulnerability scan, container image scan, image publish |
| Testing | JUnit 5, Testcontainers (Postgres + MinIO), Vitest |

## Why these choices

A few decisions worth calling out, because they weren't the default/obvious path:

- **Direct JDBC for vector writes, not Spring AI's generic `VectorStore`.** `PgVectorStoreAutoConfiguration` is explicitly excluded. The generic `VectorStore` API doesn't support first-class `organization_id`/`document_id` columns for tenant-scoped filtering, so retrieval goes through a hand-rolled `RetrievalAugmentationAdvisor` (a `QuestionAnswerAdvisor` equivalent) calling a repository that queries `document_chunk` directly.
- **DB-polling over Kafka for the ingestion queue.** At single-instance scale, a `FOR UPDATE SKIP LOCKED` polling worker does the job without the operational overhead of a broker, consumer groups, and offset management. Revisit if/when multiple backend instances need to compete for jobs, not before.
- **Guardrails ordered by cost, not just correctness.** Rate limiting and prompt-injection checks run before any model call; the groundedness check (an LLM call itself) runs last, only if every cheaper check already passed.
- **User identity re-fetched from the database on every request**, not trusted from the JWT's embedded claims — a disabled account or role change takes effect on the very next request instead of only after the access token naturally expires.

## Getting started

### Prerequisites
- Java 21, Maven (or use your IDE's bundled Maven)
- Node 22+, npm
- Docker (for Postgres/pgvector, MinIO, ClamAV via Compose)
- An [Anthropic API key](https://console.anthropic.com/) and a [Voyage AI API key](https://www.voyageai.com/)

### 1. Start infrastructure

```bash
cd infra
cp .env.example .env   # fill in DB_PASSWORD, MINIO credentials, JWT_SIGNING_KEY, ANTHROPIC_API_KEY, VOYAGE_API_KEY
docker compose up -d postgres minio clamav
```

### 2. Run the backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The backend runs Flyway migrations automatically on startup. Create the MinIO bucket referenced by your config before your first upload, via the MinIO console at `localhost:9001`.

### 3. Run the frontend

```bash
cd frontend
npm ci
npm start
```

Visit `http://localhost:4200`, register an organization, and upload a PDF.

### Or: run everything in Docker

```bash
cd infra
docker compose up --build
```

## Testing

```bash
# Backend: unit + Testcontainers-backed integration tests
cd backend && mvn verify

# Frontend: Vitest unit tests
cd frontend && npm test -- --watch=false
```

CI runs both suites plus CodeQL static analysis and a Trivy dependency/image scan on every push and PR (see `.github/workflows/`).

## Project structure

```
backend/
  src/main/java/com/documind/
    auth/         JWT issuance, refresh rotation, login/register/logout
    org/          Organization entity, storage quota reservation
    document/     Upload, storage, document listing/status
    ingestion/    Async pipeline: extraction, chunking, embedding
    chat/         Chat sessions, retrieval-augmented orchestration
    guardrail/    Input/output guardrails, audit logging, flagged interactions
    admin/        User management, usage analytics
    ai/           Voyage embedding model adapter
    common/       Security config, tenant context, error handling

frontend/
  src/app/
    core/         Auth service, JWT interceptor, route guards
    features/     Login/register, document list, chat panel

infra/
  docker-compose.yml   Postgres+pgvector, MinIO, ClamAV, backend, frontend
```

## License

MIT — see [LICENSE](LICENSE).
