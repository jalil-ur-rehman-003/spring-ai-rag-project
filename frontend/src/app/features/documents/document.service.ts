import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentListItem, DocumentUploadResponse, DocumentVisibility } from './document.models';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly httpClient = inject(HttpClient);

  listDocuments(): Observable<DocumentListItem[]> {
    return this.httpClient.get<DocumentListItem[]>(`${environment.apiBaseUrl}/documents`);
  }

  upload(file: File, visibility: DocumentVisibility = 'PRIVATE'): Observable<DocumentUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);

    return this.httpClient.post<DocumentUploadResponse>(
      `${environment.apiBaseUrl}/documents?visibility=${visibility}`,
      formData
    );
  }
}
