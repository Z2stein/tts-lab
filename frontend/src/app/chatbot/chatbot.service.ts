import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CurrentUserService } from '../current-user.service';
import { ApiErrorResponse, ChatResponse } from '../shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class ChatbotService {
  constructor(
    private readonly http: HttpClient,
    private readonly currentUserService: CurrentUserService
  ) {}

  async sendMessage(message: string, conversationId: string | null): Promise<ChatResponse> {
    try {
      const response = await firstValueFrom(this.http.post<ChatResponse>('/api/chat', { message, conversationId }));
      await this.refreshRequestLimits();
      return response;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status !== 0) {
        await this.refreshRequestLimits();
      }

      const errorBody = await this.readApiError(error);
      if (
        error instanceof HttpErrorResponse &&
        error.status === 429 &&
        (errorBody?.code === 'RATE_LIMIT_EXCEEDED' || errorBody?.error === 'RATE_LIMIT_EXCEEDED')
      ) {
        throw new Error(errorBody?.message || 'Chat usage limit exceeded. Please try again later.');
      }

      if (error instanceof HttpErrorResponse) {
        throw new Error(errorBody?.message || `Chat request failed (HTTP ${error.status}).`);
      }

      throw error;
    }
  }

  private async refreshRequestLimits(): Promise<void> {
    const service = this.currentUserService as CurrentUserService & {
      refreshRequestLimits?: () => Promise<unknown>;
    };
    await service.refreshRequestLimits?.();
  }

  private async readApiError(error: unknown): Promise<(ApiErrorResponse & { error?: string }) | null> {
    if (!(error instanceof HttpErrorResponse)) {
      return null;
    }

    const raw: unknown = error.error as unknown;
    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
      return raw as ApiErrorResponse & { error?: string };
    }

    if (raw instanceof Blob) {
      try {
        return JSON.parse(await raw.text()) as ApiErrorResponse & { error?: string };
      } catch {
        return null;
      }
    }

    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw) as ApiErrorResponse & { error?: string };
      } catch {
        return null;
      }
    }

    return null;
  }
}
