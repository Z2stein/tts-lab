import { Injectable } from '@angular/core';
import { CurrentUserService } from './current-user.service';

@Injectable({
  providedIn: 'root'
})
export class TextLengthService {
  constructor(private readonly currentUserService: CurrentUserService) {}

  async getLength(text: string): Promise<number> {
    console.info('[text-length] Sending request', { textLength: text.length });

    let response: Response;
    try {
      response = await fetch('/api/text-length', {
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
      console.error('[text-length] Backend returned non-OK status', { status: response.status });
      throw new Error(`Backend request failed (HTTP ${response.status}).`);
    }

    const data = (await response.json()) as { length: number };
    console.info('[text-length] Request succeeded', { length: data.length });
    return data.length;
  }
}
