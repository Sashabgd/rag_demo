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
