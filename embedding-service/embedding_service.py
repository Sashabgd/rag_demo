import logging
import os
import warnings
from typing import Dict, List, Optional, Tuple

import httpx
import numpy as np
import redis
from dotenv import load_dotenv
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_redis import RedisConfig, RedisVectorStore
from redis.commands.search.query import Query
from sentence_transformers import CrossEncoder

warnings.filterwarnings("ignore", category=DeprecationWarning)

load_dotenv()

log = logging.getLogger("embedding_service")

LOCAL_INDEX_NAME = "rag_demo"
METADATA_SCHEMA = [
    {"name": "document_id", "type": "numeric"},
    {"name": "chunk_id", "type": "numeric"},
    {"name": "source", "type": "tag"},
    {"name": "start_index", "type": "numeric"},
    {"name": "end_index", "type": "numeric"},
]
DELETE_BATCH_SIZE = 1000


def _is_rag_index(index_name: str) -> bool:
    return index_name == LOCAL_INDEX_NAME or index_name.startswith(LOCAL_INDEX_NAME + "_")


class OpenAICompatibleEmbeddings(Embeddings):
    """Raw HTTP client for an OpenAI-compatible /embeddings endpoint (no SDK)."""

    BATCH_SIZE = 64

    def __init__(self, base_url: str, model: str, api_key: Optional[str] = None) -> None:
        self.url = base_url.strip().rstrip("/") + "/embeddings"
        self.model = model
        self.api_key = api_key

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        vectors: List[List[float]] = []
        with httpx.Client(timeout=60.0) as client:
            for start in range(0, len(texts), self.BATCH_SIZE):
                batch = texts[start:start + self.BATCH_SIZE]
                response = client.post(self.url, headers=headers, json={"model": self.model, "input": batch})
                response.raise_for_status()
                data = sorted(response.json().get("data", []) or [], key=lambda d: d.get("index", 0))
                if len(data) != len(batch):
                    raise ValueError(f"Embedding server returned {len(data)} vectors for {len(batch)} inputs")
                vectors.extend(item["embedding"] for item in data)
        return vectors

    def embed_query(self, text: str) -> List[float]:
        return self.embed_documents([text])[0]


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
        self.index_name = LOCAL_INDEX_NAME
        self.embeddings = HuggingFaceEmbeddings(
            model_name="intfloat/multilingual-e5-small",
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )
        self.embeddings.embed_query("warmup")
        self.config = self._redis_config(self.index_name)
        self.vector = RedisVectorStore(self.embeddings, config=self.config)
        # Remote (OpenAI-compatible) models, each with its own index: (index, url, model, key) -> store
        self._remote_stores: Dict[tuple, Tuple[Embeddings, RedisVectorStore]] = {}
        self.redis_client = redis.Redis.from_url(self.redis_url)
        rerank_model = os.getenv("RERANKER_LOCAL_MODEL", "BAAI/bge-reranker-v2-m3")
        self.reranker = CrossEncoder(rerank_model, device="cpu")
        self.cohere = CohereReranker()
        self._initialized = True

    def _redis_config(self, index_name: str) -> RedisConfig:
        return RedisConfig(
            index_name=index_name,
            redis_url=self.redis_url,
            metadata_schema=[dict(field) for field in METADATA_SCHEMA],
        )

    def _resolve(self, cfg: Optional[dict]) -> Tuple[Embeddings, RedisVectorStore, str, bool]:
        """Returns (embeddings, vector store, index name, is_local) for an embedding config."""
        if not cfg or not cfg.get("baseUrl"):
            return self.embeddings, self.vector, self.index_name, True

        index_name = cfg.get("indexName") or ""
        model = cfg.get("modelName") or ""
        if index_name == LOCAL_INDEX_NAME or not _is_rag_index(index_name):
            raise ValueError(f"Invalid index name for remote embedding model: '{index_name}'")
        if not model:
            raise ValueError("modelName is required for a remote embedding model")

        key = (index_name, cfg["baseUrl"], model, cfg.get("apiKey"))
        cached = self._remote_stores.get(key)
        if cached is None:
            embeddings = OpenAICompatibleEmbeddings(cfg["baseUrl"], model, cfg.get("apiKey"))
            # Creating the store embeds a sample text to size the index, so this also checks the connection.
            store = RedisVectorStore(embeddings, config=self._redis_config(index_name))
            cached = (embeddings, store)
            self._remote_stores[key] = cached
        return cached[0], cached[1], index_name, False

    def embed_documents(self, items: List[dict], cfg: Optional[dict] = None) -> List[dict]:
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
        _, vector, _, _ = self._resolve(cfg)
        ids = vector.add_documents(documents)
        result = []
        for i, doc_id in enumerate(ids):
            chunk_id = documents[i].metadata.get("chunk_id")
            result.append({"chunkId": chunk_id, "vectorStoreId": doc_id})
        return result

    def search(
        self,
        query: str,
        top_k: int = 5,
        rerank_type: str = "LOCAL",
        k: int = 50,
        cfg: Optional[dict] = None,
    ) -> dict:
        rerank_type = (rerank_type or "LOCAL").upper()
        if rerank_type not in {"NONE", "LOCAL", "COHERE"}:
            rerank_type = "LOCAL"

        embeddings, _, index_name, is_local = self._resolve(cfg)
        # The "query: " prefix is an e5 convention; remote models get the raw query.
        query_text = f"query: {query}" if is_local else query
        query_vector = embeddings.embed_query(query_text)
        params = {"vec_param": np.array(query_vector).astype(np.float32).tobytes()}

        base_query = f"*=>[KNN {k} @embedding $vec_param AS vector_score]"
        redis_query = (
            Query(base_query)
            .sort_by("vector_score")
            .paging(0, k)
            .dialect(2)
        )

        try:
            results = self.redis_client.ft(index_name).search(redis_query, query_params=params)
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

    def _delete_matching(self, index_name: str, query: str) -> int:
        """Deletes every key in the index matching the query, in batches. Returns the deleted count."""
        deleted = 0
        redis_query = Query(query).no_content().paging(0, DELETE_BATCH_SIZE).dialect(2)
        while True:
            try:
                results = self.redis_client.ft(index_name).search(redis_query)
            except redis.ResponseError as e:
                message = str(e).lower()
                if "no such index" in message or "unknown index" in message:
                    return deleted
                raise
            keys = [doc.id for doc in results.docs]
            if not keys:
                return deleted
            removed = self.redis_client.delete(*keys)
            deleted += removed
            if removed == 0:
                return deleted

    def delete_document(self, index_name: str, document_id: int) -> int:
        if not _is_rag_index(index_name):
            raise ValueError(f"Invalid index name: '{index_name}'")
        return self._delete_matching(index_name, f"@document_id:[{document_id} {document_id}]")

    def delete_all(self) -> int:
        deleted = 0
        for raw in self.redis_client.execute_command("FT._LIST"):
            name = raw.decode("utf-8") if isinstance(raw, bytes) else str(raw)
            if _is_rag_index(name):
                deleted += self._delete_matching(name, "*")
        return deleted


def get_service() -> EmbeddingService:
    return EmbeddingService()
