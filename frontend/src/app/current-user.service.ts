import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { LoggerService } from './logger.service';
import { CurrentUser, RequestRateLimitSummary } from './shared/api-contract.generated';

export type { CurrentUser, RequestRateLimitSummary, RequestRateLimitSummaryItem } from './shared/api-contract.generated';

@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  constructor(
    private readonly http: HttpClient,
    private readonly logger: LoggerService
  ) {}

  async getCurrentUser(): Promise<CurrentUser | null> {
    this.logger.info('auth', 'Checking current user via /api/me');

    try {
      const response = await firstValueFrom(this.http.get('/api/me', { observe: 'response', responseType: 'text' }));
      const user = this.parseJsonResponse<CurrentUser>(response, '/api/me');
      if (!user) {
        return null;
      }
      this.logger.info('auth', 'User authenticated', { id: user.id, authMode: user.authMode });
      return user;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.logger.info('auth', '/api/me returned 401 (unauthenticated)');
        return null;
      }

      console.error('[auth] /api/me request failed, treating as unauthenticated', error);
      return null;
    }
  }

  async refreshRequestLimits(): Promise<RequestRateLimitSummary | null> {
    try {
      const response = await firstValueFrom(this.http.get('/api/request-limits/me', { observe: 'response', responseType: 'text' }));
      const summary = this.parseJsonResponse<RequestRateLimitSummary>(response, '/api/request-limits/me');
      if (!summary) {
        return null;
      }
      window.dispatchEvent(new CustomEvent<RequestRateLimitSummary>('request-limits-updated', { detail: summary }));
      return summary;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        return null;
      }

      console.error('[auth] /api/request-limits/me request failed', error);
      return null;
    }
  }

  startGoogleLogin(): void {
    this.logger.info('auth', 'Starting Google login redirect');
    this.navigate('/oauth2/authorization/google');
  }

  async startLogout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/logout', null, { responseType: 'text' }));
      this.logger.info('auth', 'Logout request completed, reloading page');
      this.navigate('/');
    } catch (error) {
      console.error('[auth] Logout request failed', error);
    }
  }

  private parseJsonResponse<T>(response: HttpResponse<string>, requestName: string): T | null {
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.includes('application/json')) {
      console.error(`[auth] ${requestName} returned unexpected response`, {
        status: response.status,
        contentType
      });
      return null;
    }

    if (!response.body?.trim()) {
      console.error(`[auth] ${requestName} returned an empty JSON response`, {
        status: response.status,
        contentType
      });
      return null;
    }

    try {
      return JSON.parse(response.body) as T;
    } catch (error) {
      console.error(`[auth] ${requestName} returned invalid JSON`, error);
      return null;
    }
  }

  private navigate(url: string): void {
    window.location.assign(url);
  }
}
