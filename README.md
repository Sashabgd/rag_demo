# RAG Demo

Minimalistički RAG demo za prezentaciju: upload → parse → save → chunk → embed → Redis, zatim vektor pretraga i agentic RAG sa Gemini 2.5 Flash.

## Stack

| Sloj | Tehnologija |
|------|-------------|
| UI | Next.js 15, Tailwind, shadcn/ui |
| API | Spring Boot 3.4, Java 21 |
| Embedding / rerank | Python FastAPI, e5-small, BGE reranker |
| Baze | PostgreSQL, Redis Stack (vektori) |
| LLM | Google Gemini 2.5 Flash (jedan tool: `search_documents`) |

## Preduslovi

- Docker Desktop
- Java 21
- Python 3.11+
- Node.js 20+
- `GEMINI_API_KEY` u okruženju (ili `backend/.env`)

## Pokretanje

### Opcija A — sve u Dockeru (preporučeno za demo)

```bash
# 1. API ključ (obavezno za /chat)
cp .env.example .env
# uredi .env i postavi GEMINI_API_KEY

# 2. Podigni ceo stack (prvi build traje 10–20 min zbog ML modela)
docker compose up --build -d

# 3. Prati logove embedding servisa (modeli se skidaju pri prvom startu)
docker compose logs -f embedding-service
```

Kada su svi servisi zdravi:

| Servis | URL |
|--------|-----|
| UI | http://localhost:3000 |
| Java API | http://localhost:8081 |
| Embedding API | http://localhost:8002 |
| RedisInsight | http://localhost:8001 |

Zaustavi: `docker compose down`

### Opcija B — hibridno (infra u Dockeru, kod lokalno)

#### 1. Infra (Docker)

```bash
docker compose up -d postgres redis
```

- Postgres: `localhost:5432` (db: `rag_demo`, user/pass: `rag`/`rag`)
- Redis Stack: `localhost:7379`
- RedisInsight UI: http://localhost:8001

#### 2. Python embedding servis

```bash

cd embedding-service
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # Linux/Mac
pip install -r requirements.txt
set REDIS_URL=redis://localhost:7379
uvicorn main:app --reload --port 8002
```

Prvi start skida HuggingFace modele (~500MB) i reranker (~2.3GB)


#### 3. Java backend

```bash
cd backend
set GEMINI_API_KEY=your-key-here
gradlew.bat bootRun
```

API: http://localhost:8080

#### 4. Next.js frontend

```bash
cd frontend
npm install
npm run dev ili yarn dev
```

UI: http://localhost:3000

## Demo skripta za prezentaciju

1. **Uvod** — Otvori `/` i objasni 5 koraka pipeline-a.
2. **Upload** — `/upload` → prevuci PDF ili DOCX → redirect na `/documents/{id}`.
3. **Pipeline uživo** — Prati 5 kartica dok se status menja: Upload → Parse → Save → Chunk → Embed.
4. **Redis** — Otvori RedisInsight (`http://localhost:8001`) → index `rag_demo`, pokaži vektore.
5. **Vektor pretraga** — `/search` → unesi pitanje → uključi/isključi reranker.
6. **Agentic RAG** — `/chat` → pitaj nešto iz dokumenta → pokaži `tool_call` → chunkove → finalni odgovor.

## API endpoints

| Method | Path | Opis |
|--------|------|------|
| POST | `/api/documents/upload` | Upload fajla |
| GET | `/api/documents` | Lista dokumenata |
| GET | `/api/documents/{id}` | Detalj + status pipeline-a |
| POST | `/api/search` | Direktna vektor pretraga |
| POST | `/api/chat` | SSE chat sa Gemini + RAG tool |

## Env varijable

| Varijabla | Podrazumevano | Opis |
|-----------|---------------|------|
| `GEMINI_API_KEY` | — | Google AI API ključ |
| `EMBEDDING_SERVICE_URL` | `http://localhost:8002` | Python servis |
| `REDIS_URL` | `redis://localhost:7379` | Redis (Python) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rag_demo` | Postgres |
