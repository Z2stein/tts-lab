import { Injectable } from '@angular/core';
import { CurrentUserService } from '../current-user.service';

export interface ChatResponse {
  answer: string;
  conversationId: string;
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
      throw new Error(`Chat request failed (HTTP ${response.status}).`);
    }

    return (await response.json()) as ChatResponse;
  }
}
