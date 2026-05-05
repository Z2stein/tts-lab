import { Injectable } from '@angular/core';
import { CurrentUserService } from '../current-user.service';

export interface ChatResponse {
  answer: string;
  conversationId: string;
}

interface ChatErrorResponse {
  error?: string;
  message?: string;
  retry_after?: number;
}

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

    if (!response.ok) {
      let errorBody: ChatErrorResponse | null = null;
      try {
        errorBody = (await response.json()) as ChatErrorResponse;
      } catch {
        errorBody = null;
      }

      if (response.status === 429 && errorBody?.error === 'RATE_LIMIT_EXCEEDED') {
        const retryAfter = typeof errorBody.retry_after === 'number' ? errorBody.retry_after : null;
        const retryHint = retryAfter != null ? ` Please try again in ${retryAfter} seconds.` : '';
        throw new Error('Chat rate limit reached.' + retryHint);
      }

      throw new Error(errorBody?.message || `Chat request failed (HTTP ${response.status}).`);
    }

    return (await response.json()) as ChatResponse;
  }
}
