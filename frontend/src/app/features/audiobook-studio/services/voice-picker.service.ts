import { Injectable } from '@angular/core';
import { AudiobookApiService } from '../../audiobook-shared/service/audiobook-api.service';
import { SpeakerVoiceCatalogItem } from '../../../shared/api-contract.generated';

export type { SpeakerVoiceCatalogItem };

@Injectable({ providedIn: 'root' })
export class VoicePickerService {
  constructor(private readonly apiService: AudiobookApiService) {}

  async getVoiceCatalog(): Promise<SpeakerVoiceCatalogItem[]> {
    const response = await this.apiService.getJsonResponse<SpeakerVoiceCatalogItem[]>(
      '/api/audiobooks/workflow/voices',
      'Voice catalog load failed'
    );
    if (!response.body) {
      throw new Error('Voice catalog load failed.');
    }
    return response.body;
  }
}
