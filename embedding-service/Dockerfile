FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY embedding_service.py main.py ./

ENV REDIS_URL=redis://redis:6379
ENV HF_HOME=/cache/huggingface
ENV TRANSFORMERS_CACHE=/cache/huggingface
ENV SENTENCE_TRANSFORMERS_HOME=/cache/sentence-transformers

EXPOSE 8002

HEALTHCHECK --interval=15s --timeout=10s --start-period=300s --retries=10 \
  CMD curl -f http://localhost:8002/health || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8002"]
