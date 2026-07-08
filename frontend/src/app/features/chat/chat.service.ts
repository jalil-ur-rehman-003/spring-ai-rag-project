import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { ChatSessionResponse, CreateChatSessionRequest } from './chat.models';

/**
 * The streaming endpoint returns raw SSE text chunks (see ChatController on the
 * backend), not JSON events -- HttpClient can't incrementally read a streaming
 * response body, so `fetch` is used directly for that one call, with the
 * Bearer token attached manually since fetch bypasses the jwtInterceptor --
 * including its silent refresh-and-retry on an expired token, which is
 * re-implemented here for the same reason (see jwt.interceptor.ts: the
 * backend maps an expired/invalid token to 403, not 401, since
 * AuthorizationDeniedException is what an unauthenticated request hitting
 * `.anyRequest().authenticated()` throws).
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly httpClient = inject(HttpClient);
  private readonly authService = inject(AuthService);

  createSession(request: CreateChatSessionRequest): Observable<ChatSessionResponse> {
    return this.httpClient.post<ChatSessionResponse>(`${environment.apiBaseUrl}/chat/sessions`, request);
  }

  async streamAnswer(sessionId: string, question: string, onChunk: (chunk: string) => void): Promise<void> {
    const url = `${environment.apiBaseUrl}/chat/sessions/${sessionId}/messages`;
    const body = JSON.stringify({ question });

    let response = await fetch(url, { method: 'POST', headers: this.jsonHeaders(), body });

    if (response.status === 401 || response.status === 403) {
      try {
        await firstValueFrom(this.authService.refreshAccessToken());
      } catch (refreshError) {
        this.authService.logout();
        throw refreshError;
      }
      response = await fetch(url, { method: 'POST', headers: this.jsonHeaders(), body });
    }

    if (!response.ok || !response.body) {
      throw new Error(`Chat request failed with status ${response.status}`);
    }

    const reader = response.body.getReader();
    const textDecoder = new TextDecoder();
    let unterminatedLine = '';

    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      const lines = (unterminatedLine + textDecoder.decode(value, { stream: true })).split('\n');
      unterminatedLine = lines.pop() ?? '';

      const chunk = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice('data:'.length))
        .join('');
      if (chunk) {
        onChunk(chunk);
      }
    }
  }

  private jsonHeaders(): HeadersInit {
    const accessToken = this.authService.readAccessToken();
    return {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    };
  }
}
