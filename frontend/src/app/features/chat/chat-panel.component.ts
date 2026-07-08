import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ChatMessage } from './chat.models';
import { ChatService } from './chat.service';

/** Collection-wide chat: asks questions across every READY document in the org (no documentId scoping). */
@Component({
  selector: 'documind-chat-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './chat-panel.component.html',
  styleUrl: './chat-panel.component.scss',
})
export class ChatPanelComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly chatService = inject(ChatService);

  readonly messages = signal<ChatMessage[]>([]);
  readonly isAsking = signal(false);
  readonly askError = signal<string | null>(null);

  private sessionId: string | null = null;

  readonly questionForm = this.formBuilder.nonNullable.group({
    question: ['', [Validators.required]],
  });

  submitQuestion(): void {
    if (this.questionForm.invalid) {
      this.questionForm.markAllAsTouched();
      return;
    }

    const { question } = this.questionForm.getRawValue();
    this.isAsking.set(true);
    this.askError.set(null);
    this.messages.update((current) => [...current, { role: 'user', content: question }]);
    this.questionForm.reset();

    this.ensureSession()
      .then((sessionId) => this.streamAnswerForQuestion(sessionId, question))
      .catch(() => {
        this.isAsking.set(false);
        this.askError.set('Could not reach the chat service.');
      });
  }

  private async ensureSession(): Promise<string> {
    if (this.sessionId) {
      return this.sessionId;
    }

    const session = await new Promise<string>((resolve, reject) => {
      this.chatService.createSession({ documentId: null, title: null }).subscribe({
        next: (response) => resolve(response.sessionId),
        error: reject,
      });
    });

    this.sessionId = session;
    return session;
  }

  private async streamAnswerForQuestion(sessionId: string, question: string): Promise<void> {
    this.messages.update((current) => [...current, { role: 'assistant', content: '' }]);

    try {
      await this.chatService.streamAnswer(sessionId, question, (chunk) => this.appendToLastAssistantMessage(chunk));
    } catch {
      // The underlying fetch stream can reject after all content has already
      // been delivered via onChunk (the connection closing without a
      // Content-Length trips ERR_INCOMPLETE_CHUNKED_ENCODING even on a
      // successful SSE completion) -- only a genuinely empty reply means the
      // question actually went unanswered.
      if (this.lastAssistantMessageIsEmpty()) {
        this.askError.set('The assistant could not answer that question.');
      }
    } finally {
      this.isAsking.set(false);
    }
  }

  private lastAssistantMessageIsEmpty(): boolean {
    const current = this.messages();
    return current[current.length - 1]?.content === '';
  }

  private appendToLastAssistantMessage(chunk: string): void {
    this.messages.update((current) => {
      const updated = [...current];
      const lastIndex = updated.length - 1;
      updated[lastIndex] = { ...updated[lastIndex], content: updated[lastIndex].content + chunk };
      return updated;
    });
  }
}
