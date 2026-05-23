import logging
import os
import warnings
from typing import List, Optional

import httpx
import numpy as np
import redis
from dotenv import load_dotenv
from langchain_core.documents import Document
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_redis import RedisConfig, RedisVectorStore
from redis.commands.search.query import Query
from sentence_transformers import CrossEncoder

warnings.filterwarnings("ignore", category=DeprecationWarning)

load_dotenv()

log = logging.getLogger("embedding_service")


class CohereReranker:
    """Raw HTTP client for Cohere /v1/rerank (no SDK)."""

    def __init__(self) -> None:
        self.model = os.getenv("RERANKER_COHERE_MODEL", "rerank-v4.0-pro")
        self.base_url = os.getenv("RERANK_API_URL", "https://api.cohere.com/v1/rerank")
        self.token = os.getenv("RERANK_API_TOKEN", "")

    def is_configured(self) -> bool:
        return bool(self.token)

    def rerank(self, query: str, contents: List[str]) -> Optional[List[float]]:
        if not self.token or not contents:
            return None
        payload = {
            "model": self.model,
            "query": query,
            "documents": contents,
            "return_documents": False,
        }
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json",
        }
        with httpx.Client(timeout=30.0) as client:
            response = client.post(self.base_url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()

        scores = [0.0] * len(contents)
        for item in data.get("results", []) or []:
            idx = int(item.get("index", -1))
            if 0 <= idx < len(scores):
                scores[idx] = float(item.get("relevance_score", 0.0))
        return scores


class EmbeddingService:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self.redis_url = os.getenv("REDIS_URL", "redis://localhost:7379")
        self.index_name = "rag_demo"
        self.embeddings = HuggingFaceEmbeddings(
            model_name="intfloat/multilingual-e5-small",
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )
        self.embeddings.embed_query("warmup")
        self.config = RedisConfig(
            index_name=self.index_name,
            redis_url=self.redis_url,
            metadata_schema=[
                {"name": "document_id", "type": "numeric"},
                {"name": "chunk_id", "type": "numeric"},
                {"name": "source", "type": "tag"},
                {"name": "start_index", "type": "numeric"},
                {"name": "end_index", "type": "numeric"},
            ],
        )
        self.vector = RedisVectorStore(self.embeddings, config=self.config)
        self.redis_client = redis.Redis.from_url(self.redis_url)
        rerank_model = os.getenv("RERANKER_LOCAL_MODEL", "BAAI/bge-reranker-v2-m3")
        self.reranker = CrossEncoder(rerank_model, device="cpu")
        self.cohere = CohereReranker()
        self._initialized = True

    def embed_documents(self, items: List[dict]) -> List[dict]:
        documents = []
        for item in items:
            meta = item.get("metadata") or {}
            documents.append(
                Document(
                    page_content=item["content"],
                    metadata={
                        "document_id": meta.get("document_id", 0),
                        "chunk_id": meta.get("chunk_id", 0),
                        "source": str(meta.get("source", "")),
                        "start_index": meta.get("start_index", 0),
                        "end_index": meta.get("end_index", 0),
                    },
                )
            )
        if not documents:
            return []
        ids = self.vector.add_documents(documents)
        result = []
        for i, doc_id in enumerate(ids):
            chunk_id = documents[i].metadata.get("chunk_id")
            result.append({"chunkId": chunk_id, "vectorStoreId": doc_id})
        return result

    def search(self, query: str, top_k: int = 5, rerank_type: str = "LOCAL", k: int = 50) -> dict:
        rerank_type = (rerank_type or "LOCAL").upper()
        if rerank_type not in {"NONE", "LOCAL", "COHERE"}:
            rerank_type = "LOCAL"

        query_text = f"query: {query}"
        query_vector = self.embeddings.embed_query(query_text)
        params = {"vec_param": np.array(query_vector).astype(np.float32).tobytes()}

        base_query = f"*=>[KNN {k} @embedding $vec_param AS vector_score]"
        redis_query = (
            Query(base_query)
            .sort_by("vector_score")
            .paging(0, k)
            .dialect(2)
        )

        try:
            results = self.redis_client.ft(self.index_name).search(redis_query, query_params=params)
        except Exception as e:
            return {"results": [], "rerankType": "NONE", "billedDocuments": 0, "error": str(e)}

        candidates = []
        for doc in results.docs:
            score = 1.0 - float(getattr(doc, "vector_score", 0) or 0)
            content = getattr(doc, "text", None) or getattr(doc, "content", "") or ""
            if not content:
                raw = self.redis_client.hget(doc.id, "text")
                if raw:
                    content = raw.decode("utf-8", errors="replace") if isinstance(raw, bytes) else str(raw)
            if isinstance(content, bytes):
                content = content.decode("utf-8", errors="replace")
            source = getattr(doc, "source", "") or ""
            if isinstance(source, bytes):
                source = source.decode("utf-8", errors="replace")
            candidates.append({
                "content": content,
                "source": source,
                "documentId": int(getattr(doc, "document_id", 0) or 0),
                "chunkId": int(getattr(doc, "chunk_id", 0) or 0),
                "startIndex": int(getattr(doc, "start_index", 0) or 0),
                "endIndex": int(getattr(doc, "end_index", 0) or 0),
                "score": score,
            })

        if not candidates:
            return {"results": [], "rerankType": "NONE", "billedDocuments": 0}

        applied_type = "NONE"
        billed = 0

        if rerank_type != "NONE" and len(candidates) > 1:
            contents = [c["content"] for c in candidates]
            if rerank_type == "COHERE":
                scores = None
                try:
                    scores = self.cohere.rerank(query, contents)
                except Exception as e:
                    log.warning("Cohere rerank failed, falling back to NONE: %s", e)
                    scores = None
                if scores is not None:
                    for i, rs in enumerate(scores):
                        candidates[i]["score"] = float(rs)
                    candidates.sort(key=lambda x: x["score"], reverse=True)
                    applied_type = "COHERE"
                    billed = len(candidates)
                else:
                    applied_type = "NONE"
                    billed = 0
            else:  # LOCAL
                pairs = [(query, c) for c in contents]
                rerank_scores = self.reranker.predict(pairs)
                for i, rs in enumerate(rerank_scores):
                    candidates[i]["score"] = float(rs)
                candidates.sort(key=lambda x: x["score"], reverse=True)
                applied_type = "LOCAL"
                billed = len(candidates)

        return {
            "results": candidates[:top_k],
            "rerankType": applied_type,
            "billedDocuments": billed,
        }


def get_service() -> EmbeddingService:
    return EmbeddingService()
