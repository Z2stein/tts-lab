import { Injectable } from '@angular/core';
import { ModelType, PromptHistoryItem, PromptHistoryResponse } from '../../shared/api-contract.generated';

export type { ModelType, PromptHistoryItem, PromptHistoryResponse } from '../../shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class PromptHistoryService {
  async getHistory(modelType?: ModelType): Promise<PromptHistoryItem[]> {
    const query = modelType ? `?modelType=${encodeURIComponent(modelType)}` : '';
    const response = await fetch(`/api/prompts/history${query}`);

    if (!response.ok) {
      throw new Error(`Prompt history failed (HTTP ${response.status}).`);
    }

    const data = (await response.json()) as PromptHistoryResponse;
    return data.items;
  }
}
