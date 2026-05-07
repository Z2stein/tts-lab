import { Injectable } from '@angular/core';

export type PromptModelType = 'TEXT_MODEL' | 'SPEECH_MODEL';

export interface PromptHistoryItem {
  id: number;
  userId: string;
  userEmail: string | null;
  modelType: PromptModelType;
  providerModelName: string | null;
  promptText: string;
  requestStatus: string;
  createdAt: string;
}

interface PromptHistoryResponse {
  items: PromptHistoryItem[];
}

@Injectable({ providedIn: 'root' })
export class PromptHistoryService {
  async getHistory(modelType?: PromptModelType): Promise<PromptHistoryItem[]> {
    const query = modelType ? `?modelType=${encodeURIComponent(modelType)}` : '';
    const response = await fetch(`/api/prompts/history${query}`);

    if (!response.ok) {
      throw new Error(`Prompt history failed (HTTP ${response.status}).`);
    }

    const data = (await response.json()) as PromptHistoryResponse;
    return data.items;
  }
}
