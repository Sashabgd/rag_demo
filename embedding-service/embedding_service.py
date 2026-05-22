import os
import warnings
from typing import List, Optional

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

    def search(self, query: str, top_k: int = 5, rerank: bool = True, k: int = 50) -> dict:
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

        if rerank and len(candidates) > 1:
            pairs = [(query, c["content"]) for c in candidates]
            rerank_scores = self.reranker.predict(pairs)
            for i, rs in enumerate(rerank_scores):
                candidates[i]["score"] = float(rs)
            candidates.sort(key=lambda x: x["score"], reverse=True)
            rerank_type = "LOCAL"
        else:
            rerank_type = "NONE"

        return {
            "results": candidates[:top_k],
            "rerankType": rerank_type,
            "billedDocuments": len(candidates),
        }


def get_service() -> EmbeddingService:
    return EmbeddingService()
