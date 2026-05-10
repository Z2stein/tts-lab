import { Injectable } from '@angular/core';
import { CurrentUserService } from '../../current-user.service';
import { LoggerService } from '../../logger.service';

interface ApiErrorResponse {
  status?: number;
  code?: string;
  message?: string;
  details?: string | null;
  requestId?: string;
}

@Injectable({
  providedIn: 'root'
})
export class TextLengthService {
  constructor(
    private readonly currentUserService: CurrentUserService,
    private readonly logger: LoggerService
  ) {}

  async getLength(text: string): Promise<number> {
    this.logger.info('text-length', 'Sending request', { textLength: text.length });

    let response: Response;
    try {
      response = await fetch('/api/projects/text-length/calculate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
        },
        body: JSON.stringify({ text })
      });
    } catch (error) {
      console.error('[text-length] Network error while calling backend', error);
      throw new Error('Backend is unreachable. Please try again in a moment.');
    }

    if (!response.ok) {
      const apiError = await this.readApiError(response);
      console.error('[text-length] Backend returned non-OK status', { status: response.status, code: apiError?.code });
      throw new Error(apiError?.message || `Backend request failed (HTTP ${response.status}).`);
    }

    const data = (await response.json()) as { length: number };
    this.logger.info('text-length', 'Request succeeded', { length: data.length });
    return data.length;
  }

  private async readApiError(response: Response): Promise<ApiErrorResponse | null> {
    try {
      return (await response.json()) as ApiErrorResponse;
    } catch {
      return null;
    }
  }
}
