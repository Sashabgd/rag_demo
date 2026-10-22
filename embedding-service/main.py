from contextlib import asynccontextmanager
from typing import Any, List, Literal, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from embedding_service import get_service


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Preload models on startup (first request would otherwise timeout Java client)
    get_service()
    yield


app = FastAPI(title="RAG Embedding Service", version="1.0.0", lifespan=lifespan)


class EmbeddingConfig(BaseModel):
    """Which embedding model/index to use. Without baseUrl the local e5 model and index rag_demo are used."""
    indexName: str = "rag_demo"
    baseUrl: Optional[str] = None
    modelName: Optional[str] = None
    apiKey: Optional[str] = None


class EmbedDocumentItem(BaseModel):
    chunkId: int
    content: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class EmbedRequest(BaseModel):
    documents: List[EmbedDocumentItem]
    embedding: Optional[EmbeddingConfig] = None


class EmbedIdMapping(BaseModel):
    chunkId: int
    vectorStoreId: str


class EmbedResponse(BaseModel):
    ids: List[EmbedIdMapping]


class SearchRequest(BaseModel):
    query: str
    topK: int = 5
    rerankType: Literal["NONE", "LOCAL", "COHERE"] = "LOCAL"
    embedding: Optional[EmbeddingConfig] = None


class SearchResultItem(BaseModel):
    content: str
    source: str
    documentId: int
    chunkId: int
    startIndex: int
    endIndex: int
    score: float


class SearchResponse(BaseModel):
    results: List[SearchResultItem]
    rerankType: str
    billedDocuments: int


class DeleteResponse(BaseModel):
    deleted: int


def _config(embedding: Optional[EmbeddingConfig]) -> Optional[dict]:
    return embedding.model_dump() if embedding else None


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest):
    service = get_service()
    items = [
        {"content": d.content, "metadata": {**d.metadata, "chunk_id": d.chunkId}}
        for d in request.documents
    ]
    try:
        ids = service.embed_documents(items, cfg=_config(request.embedding))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return EmbedResponse(
        ids=[EmbedIdMapping(chunkId=i["chunkId"], vectorStoreId=i["vectorStoreId"]) for i in ids]
    )


@app.post("/search", response_model=SearchResponse)
def search(request: SearchRequest):
    service = get_service()
    try:
        result = service.search(
            request.query,
            top_k=request.topK,
            rerank_type=request.rerankType,
            cfg=_config(request.embedding),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return SearchResponse(
        results=[SearchResultItem(**r) for r in result["results"]],
        rerankType=result.get("rerankType", "NONE"),
        billedDocuments=result.get("billedDocuments", 0),
    )


@app.delete("/documents/{document_id}", response_model=DeleteResponse)
def delete_document(document_id: int, index: str = "rag_demo"):
    try:
        deleted = get_service().delete_document(index, document_id)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return DeleteResponse(deleted=deleted)


@app.delete("/documents", response_model=DeleteResponse)
def delete_all_documents():
    return DeleteResponse(deleted=get_service().delete_all())


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)
