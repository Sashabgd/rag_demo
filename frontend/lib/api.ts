import axios from 'axios';

export const httpClient = axios.create({
  baseURL: '',
  timeout: 10 * 60 * 1000,
});

export function extractError(e: unknown): string {
  if (axios.isAxiosError(e)) {
    return (e.response?.data as { error?: string } | undefined)?.error || e.message;
  }
  return e instanceof Error ? e.message : 'Nepoznata greška';
}

export type DocumentStatus = 'UPLOADED' | 'PARSED' | 'CHUNKED' | 'EMBEDDED' | 'FAILED';

export interface DocumentSummary {
  id: number;
  name: string;
  fileType: string;
  fileSize: number;
  status: DocumentStatus;
  uploadedAt: string;
  chunkCount: number;
  embeddingModel: string | null;
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
  embeddingModel: string | null;
  embeddingIndex: string | null;
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

export type RerankType = 'NONE' | 'LOCAL' | 'COHERE';

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

export async function deleteDocument(id: number): Promise<void> {
  await httpClient.delete(`/api/documents/${id}`);
}

export async function deleteAllDocuments(): Promise<void> {
  await httpClient.delete('/api/documents');
}

export async function searchDocuments(
  query: string,
  topK = 5,
  rerankType: RerankType = 'LOCAL',
): Promise<SearchResponse> {
  const res = await httpClient.post<SearchResponse>('/api/search', { query, topK, rerankType });
  return res.data;
}

export interface CustomModel {
  id: number;
  name: string;
  baseUrl: string;
  modelName: string;
  hasApiKey: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CustomModelInput {
  name: string;
  baseUrl: string;
  modelName: string;
  apiKey?: string;
  enabled?: boolean;
}

export async function listModels(): Promise<CustomModel[]> {
  const res = await httpClient.get<CustomModel[]>('/api/models');
  return res.data;
}

export async function createModel(input: CustomModelInput): Promise<CustomModel> {
  const res = await httpClient.post<CustomModel>('/api/models', input);
  return res.data;
}

export async function updateModel(id: number, input: CustomModelInput): Promise<CustomModel> {
  const res = await httpClient.put<CustomModel>(`/api/models/${id}`, input);
  return res.data;
}

export async function deleteModel(id: number): Promise<void> {
  await httpClient.delete(`/api/models/${id}`);
}

export interface EmbeddingModel {
  id: number;
  name: string;
  baseUrl: string;
  modelName: string;
  hasApiKey: boolean;
  active: boolean;
  indexName: string;
  createdAt: string;
  updatedAt: string;
}

export interface EmbeddingModelInput {
  name: string;
  baseUrl: string;
  modelName: string;
  apiKey?: string;
}

export async function listEmbeddingModels(): Promise<EmbeddingModel[]> {
  const res = await httpClient.get<EmbeddingModel[]>('/api/embedding-models');
  return res.data;
}

export async function createEmbeddingModel(input: EmbeddingModelInput): Promise<EmbeddingModel> {
  const res = await httpClient.post<EmbeddingModel>('/api/embedding-models', input);
  return res.data;
}

export async function updateEmbeddingModel(
  id: number,
  input: EmbeddingModelInput,
): Promise<EmbeddingModel> {
  const res = await httpClient.put<EmbeddingModel>(`/api/embedding-models/${id}`, input);
  return res.data;
}

export async function deleteEmbeddingModel(id: number): Promise<void> {
  await httpClient.delete(`/api/embedding-models/${id}`);
}

/** null switches back to the local e5-small model. */
export async function setActiveEmbeddingModel(id: number | null): Promise<void> {
  await httpClient.put('/api/embedding-models/active', { id });
}
