import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AudiobookDetailResponse, AudiobookSummary, AudiobookSummaryResponse } from '../../../shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class AudiobookLibraryService {
  constructor(private readonly http: HttpClient) {}

  async list(): Promise<AudiobookSummary[]> {
    try {
      const response = await firstValueFrom(this.http.get<AudiobookSummaryResponse>('/api/audiobooks'));
      return response.items;
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        throw new Error(`Audiobook library failed (HTTP ${error.status}).`);
      }
      throw error;
    }
  }

  async detail(id: string): Promise<AudiobookDetailResponse> {
    try {
      return await firstValueFrom(this.http.get<AudiobookDetailResponse>(`/api/audiobooks/${encodeURIComponent(id)}`));
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        throw new Error(`Audiobook detail failed (HTTP ${error.status}).`);
      }
      throw error;
    }
  }

  async updateTitle(id: string, title: string): Promise<AudiobookDetailResponse> {
    try {
      return await firstValueFrom(this.http.patch<AudiobookDetailResponse>(`/api/audiobooks/${encodeURIComponent(id)}`, { title }));
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        throw new Error(`Audiobook title update failed (HTTP ${error.status}).`);
      }
      throw error;
    }
  }
}
