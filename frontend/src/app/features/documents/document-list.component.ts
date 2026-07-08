import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { ChatPanelComponent } from '../chat/chat-panel.component';
import { DocumentListItem } from './document.models';
import { DocumentService } from './document.service';

@Component({
  selector: 'documind-document-list',
  standalone: true,
  imports: [ChatPanelComponent, DatePipe, DecimalPipe],
  templateUrl: './document-list.component.html',
  styleUrl: './document-list.component.scss',
})
export class DocumentListComponent {
  protected readonly authService = inject(AuthService);
  private readonly documentService = inject(DocumentService);

  readonly documents = signal<DocumentListItem[]>([]);
  readonly isUploading = signal(false);
  readonly uploadError = signal<string | null>(null);

  constructor() {
    this.refreshDocuments();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.isUploading.set(true);
    this.uploadError.set(null);

    this.documentService.upload(file).subscribe({
      next: () => {
        this.isUploading.set(false);
        input.value = '';
        this.refreshDocuments();
      },
      error: () => {
        this.isUploading.set(false);
        input.value = '';
        this.uploadError.set('Could not upload the document. Only PDF files are supported.');
      },
    });
  }

  private refreshDocuments(): void {
    this.documentService.listDocuments().subscribe((documents) => this.documents.set(documents));
  }
}
