from contextlib import asynccontextmanager
from typing import Any, List, Literal

from fastapi import FastAPI
from pydantic import BaseModel, Field

from embedding_service import get_service


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Preload models on startup (first request would otherwise timeout Java client)
    get_service()
    yield


app = FastAPI(title="RAG Embedding Service", version="1.0.0", lifespan=lifespan)


class EmbedDocumentItem(BaseModel):
    chunkId: int
    content: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class EmbedRequest(BaseModel):
    documents: List[EmbedDocumentItem]


class EmbedIdMapping(BaseModel):
    chunkId: int
    vectorStoreId: str


class EmbedResponse(BaseModel):
    ids: List[EmbedIdMapping]


class SearchRequest(BaseModel):
    query: str
    topK: int = 5
    rerankType: Literal["NONE", "LOCAL", "COHERE"] = "LOCAL"


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
    ids = service.embed_documents(items)
    return EmbedResponse(
        ids=[EmbedIdMapping(chunkId=i["chunkId"], vectorStoreId=i["vectorStoreId"]) for i in ids]
    )


@app.post("/search", response_model=SearchResponse)
def search(request: SearchRequest):
    service = get_service()
    result = service.search(request.query, top_k=request.topK, rerank_type=request.rerankType)
    return SearchResponse(
        results=[SearchResultItem(**r) for r in result["results"]],
        rerankType=result.get("rerankType", "NONE"),
        billedDocuments=result.get("billedDocuments", 0),
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)
