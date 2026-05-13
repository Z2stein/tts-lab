import { Injectable } from '@angular/core';
import { CurrentUserService } from '../current-user.service';
import { ApiErrorResponse, ChatResponse } from '../shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class ChatbotService {
  constructor(private readonly currentUserService: CurrentUserService) {}

  async sendMessage(message: string, conversationId: string | null): Promise<ChatResponse> {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
      },
      body: JSON.stringify({ message, conversationId })
    });
    await this.refreshRequestLimits();

    if (!response.ok) {
      let errorBody: (ApiErrorResponse & { error?: string }) | null = null;
      try {
        errorBody = (await response.json()) as ApiErrorResponse & { error?: string };
      } catch {
        errorBody = null;
      }

      if (response.status === 429 && (errorBody?.code === 'RATE_LIMIT_EXCEEDED' || errorBody?.error === 'RATE_LIMIT_EXCEEDED')) {
        throw new Error(errorBody?.message || 'Chat usage limit exceeded. Please try again later.');
      }

      throw new Error(errorBody?.message || `Chat request failed (HTTP ${response.status}).`);
    }

    return (await response.json()) as ChatResponse;
  }

  private async refreshRequestLimits(): Promise<void> {
    const service = this.currentUserService as CurrentUserService & {
      refreshRequestLimits?: () => Promise<unknown>;
    };
    await service.refreshRequestLimits?.();
  }
}
