import axios from 'axios';

export const httpClient = axios.create({
  baseURL: '',
  timeout: 10 * 60 * 1000,
});

export type DocumentStatus = 'UPLOADED' | 'PARSED' | 'CHUNKED' | 'EMBEDDED' | 'FAILED';

export interface DocumentSummary {
  id: number;
  name: string;
  fileType: string;
  fileSize: number;
  status: DocumentStatus;
  uploadedAt: string;
  chunkCount: number;
}

export interface ChunkDto {
  id: number;
  startIndex: number;
  endIndex: number;
  contentPreview: string;
  vectorStoreId: string | null;
  status: string;
}

export interface DocumentDetail {
  id: number;
  name: string;
  fileType: string;
  fileSize: number;
  status: DocumentStatus;
  uploadedAt: string;
  parsedAt: string | null;
  chunkedAt: string | null;
  embeddedAt: string | null;
  textLength: number | null;
  textPreview: string | null;
  chunks: ChunkDto[];
}

export interface SearchResultItem {
  content: string;
  source: string;
  documentId: number;
  chunkId: number;
  startIndex: number;
  endIndex: number;
  score: number;
}

export interface SearchResponse {
  results: SearchResultItem[];
  rerankType: string;
  billedDocuments: number;
}

export async function uploadDocument(file: File): Promise<DocumentSummary> {
  const formData = new FormData();
  formData.append('file', file, file.name);
  const res = await httpClient.post<DocumentSummary>('/api/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function listDocuments(): Promise<DocumentSummary[]> {
  const res = await httpClient.get<DocumentSummary[]>('/api/documents');
  return res.data;
}

export async function getDocument(id: number): Promise<DocumentDetail> {
  const res = await httpClient.get<DocumentDetail>(`/api/documents/${id}`);
  return res.data;
}

export async function searchDocuments(
  query: string,
  topK = 5,
  rerank = true,
): Promise<SearchResponse> {
  const res = await httpClient.post<SearchResponse>('/api/search', { query, topK, rerank });
  return res.data;
}
