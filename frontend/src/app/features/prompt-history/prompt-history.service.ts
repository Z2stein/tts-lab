import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ModelType, PromptHistoryItem, PromptHistoryResponse } from '../../shared/api-contract.generated';

export type { ModelType, PromptHistoryItem, PromptHistoryResponse } from '../../shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class PromptHistoryService {
  constructor(private readonly http: HttpClient) {}

  async getHistory(modelType?: ModelType): Promise<PromptHistoryItem[]> {
    const query = modelType ? `?modelType=${encodeURIComponent(modelType)}` : '';
    try {
      const response = await firstValueFrom(this.http.get<PromptHistoryResponse>(`/api/prompts/history${query}`));
      return response.items;
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        throw new Error(`Prompt history failed (HTTP ${error.status}).`);
      }
      throw error;
    }
  }
}
