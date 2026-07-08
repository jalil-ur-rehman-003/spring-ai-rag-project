// Shapes mirror the backend's chat DTOs (com.documind.chat.web) exactly, so a
// change to one side is a visible contract break rather than a silent mismatch.

export interface CreateChatSessionRequest {
  documentId: string | null;
  title: string | null;
}

export interface ChatSessionResponse {
  sessionId: string;
  documentId: string | null;
  title: string | null;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}
