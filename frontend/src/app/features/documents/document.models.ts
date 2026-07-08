// Shapes mirror the backend's document DTOs (com.documind.document.web) exactly, so a
// change to one side is a visible contract break rather than a silent mismatch.

export type DocumentVisibility = 'PRIVATE' | 'ORGANIZATION';

export type DocumentStatus = 'PENDING' | 'EXTRACTING' | 'CHUNKING' | 'JSONL_STAGED' | 'EMBEDDING' | 'READY' | 'FAILED';

export interface DocumentListItem {
  documentId: string;
  title: string;
  originalFilename: string;
  sizeBytes: number;
  visibility: DocumentVisibility;
  status: DocumentStatus;
  failureReason: string | null;
  createdAt: string;
}

export interface DocumentUploadResponse {
  documentId: string;
  status: DocumentStatus;
}
