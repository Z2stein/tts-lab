import { Injectable } from '@angular/core';
import { AudiobookDetailResponse, AudiobookSummary, AudiobookSummaryResponse } from '../../../shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class AudiobookLibraryService {
  async list(): Promise<AudiobookSummary[]> {
    const response = await fetch('/api/audiobooks');
    if (!response.ok) {
      throw new Error(`Audiobook library failed (HTTP ${response.status}).`);
    }
    const raw: unknown = await response.json();
    const data = raw as AudiobookSummaryResponse;
    return data.items;
  }

  async detail(id: string): Promise<AudiobookDetailResponse> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(id)}`);
    if (!response.ok) {
      throw new Error(`Audiobook detail failed (HTTP ${response.status}).`);
    }
    const raw: unknown = await response.json();
    return raw as AudiobookDetailResponse;
  }
}
