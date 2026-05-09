import { Injectable } from '@angular/core';
import { AudiobookDetail, AudiobookSummary, AudiobookSummaryResponse } from '../models/audiobook-library.types';

@Injectable({ providedIn: 'root' })
export class AudiobookLibraryService {
  async list(): Promise<AudiobookSummary[]> {
    const response = await fetch('/api/audiobooks');
    if (!response.ok) {
      throw new Error(`Audiobook library failed (HTTP ${response.status}).`);
    }
    const data = (await response.json()) as AudiobookSummaryResponse;
    return data.items;
  }

  async detail(id: string): Promise<AudiobookDetail> {
    const response = await fetch(`/api/audiobooks/${encodeURIComponent(id)}`);
    if (!response.ok) {
      throw new Error(`Audiobook detail failed (HTTP ${response.status}).`);
    }
    return (await response.json()) as AudiobookDetail;
  }
}
