import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { CurrentUserService } from '../../../current-user.service';
import { ApiErrorResponse, SingleSpeakerRenderPlanResponse, SingleSpeakerRenderRequest } from '../../../shared/api-contract.generated';
import { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

export type { SingleSpeakerRenderPlanResponse, SingleSpeakerRenderRequest } from '../../../shared/api-contract.generated';
export type { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

@Injectable({ providedIn: 'root' })
export class AudiobookApiService {
  constructor(
    private readonly http: HttpClient,
    private readonly currentUserService: CurrentUserService
  ) {}

  async createAudio(renderPlan: SingleSpeakerRenderPlanResponse, options: RequestOptions = {}, projectId?: string): Promise<CreatedAudioDownload> {
    const url = projectId ? `/api/audiobooks/workflow/create-audio?projectId=${encodeURIComponent(projectId)}` : '/api/audiobooks/workflow/create-audio';
    const response = await this.postBlobResponse(url, renderPlan, 'Audio creation failed', options);

    if (!response.body) {
      throw new Error(`Audio creation failed (HTTP ${response.status}).`);
    }

    return {
      blob: response.body,
      filename: this.filenameFromContentDisposition(response.headers.get('Content-Disposition')) || 'tts-render-request-1.mp3',
      projectId: response.headers.get('X-Audiobook-Project-Id') || undefined
    };
  }

  async createAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    options: RequestOptions = {},
    projectId?: string
  ): Promise<CreatedAudioDownload> {
    return this.createAudio({ renderRequests: [renderRequest] }, options, projectId);
  }

  async post<T>(url: string, body: unknown, errorPrefix: string, options: RequestOptions = {}): Promise<T> {
    const response = await this.postJsonResponse<T>(url, body, errorPrefix, options);

    if (response.body === null || response.body === undefined) {
      throw new Error(`${errorPrefix} (HTTP ${response.status}).`);
    }

    return response.body;
  }

  async getJsonResponse<T>(url: string, errorPrefix: string, options: RequestOptions = {}): Promise<HttpResponse<T>> {
    try {
      const response = await this.executeRequest(
        this.http.get<T>(url, {
          observe: 'response' as const
        }),
        options.signal
      );
      await this.refreshRequestLimits();
      return response;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status !== 0) {
        await this.refreshRequestLimits();
      }

      const apiError = await this.readApiError(error);
      if (error instanceof HttpErrorResponse) {
        throw new Error(apiError?.message || `${errorPrefix} (HTTP ${error.status}).`);
      }

      throw error;
    }
  }

  async postJsonResponse<T>(url: string, body: unknown, errorPrefix: string, options: RequestOptions = {}): Promise<HttpResponse<T>> {
    try {
      const response = await this.executeRequest(
        this.http.post<T>(url, body, {
          observe: 'response' as const
        }),
        options.signal
      );
      await this.refreshRequestLimits();
      return response;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status !== 0) {
        await this.refreshRequestLimits();
      }

      const apiError = await this.readApiError(error);
      if (error instanceof HttpErrorResponse) {
        throw new Error(apiError?.message || `${errorPrefix} (HTTP ${error.status}).`);
      }

      throw error;
    }
  }

  async postBlobResponse(url: string, body: unknown, errorPrefix: string, options: RequestOptions = {}): Promise<HttpResponse<Blob>> {
    try {
      const response = await this.executeRequest(
        this.http.post(url, body, {
          observe: 'response' as const,
          responseType: 'blob' as const
        }),
        options.signal
      );
      await this.refreshRequestLimits();
      return response;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status !== 0) {
        await this.refreshRequestLimits();
      }

      const apiError = await this.readApiError(error);
      if (error instanceof HttpErrorResponse) {
        throw new Error(apiError?.message || `${errorPrefix} (HTTP ${error.status}).`);
      }

      throw error;
    }
  }

  async patchJsonResponse<T>(url: string, body: unknown, errorPrefix: string, options: RequestOptions = {}): Promise<HttpResponse<T>> {
    try {
      const response = await this.executeRequest(
        this.http.patch<T>(url, body, {
          observe: 'response' as const
        }),
        options.signal
      );
      await this.refreshRequestLimits();
      return response;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status !== 0) {
        await this.refreshRequestLimits();
      }

      const apiError = await this.readApiError(error);
      if (error instanceof HttpErrorResponse) {
        throw new Error(apiError?.message || `${errorPrefix} (HTTP ${error.status}).`);
      }

      throw error;
    }
  }

  private filenameFromContentDisposition(contentDisposition: string | null): string | null {
    if (!contentDisposition) {
      return null;
    }

    const match = /filename="?([^";]+)"?/i.exec(contentDisposition);
    return match ? match[1] : null;
  }

  private async refreshRequestLimits(): Promise<void> {
    await this.currentUserService.refreshRequestLimits();
  }

  private async executeRequest<T>(request: Observable<HttpResponse<T>>, signal?: AbortSignal): Promise<HttpResponse<T>> {
    if (!signal) {
      return firstValueFrom(request);
    }

    if (signal.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }

    return new Promise<HttpResponse<T>>((resolve, reject) => {
      let settled = false;

      const finalize = (callback: () => void): void => {
        if (settled) {
          return;
        }
        settled = true;
        signal.removeEventListener('abort', onAbort);
        callback();
      };

      const onAbort = (): void => {
        subscription.unsubscribe();
        finalize(() => reject(new DOMException('Aborted', 'AbortError')));
      };

      const subscription = request.subscribe({
        next: (response) => finalize(() => resolve(response)),
        error: (error) => finalize(() => reject(error)),
        complete: () => {
          if (!settled) {
            finalize(() => reject(new Error('Request completed without a response.')));
          }
        }
      });

      signal.addEventListener('abort', onAbort, { once: true });
    });
  }

  private async readApiError(error: unknown): Promise<ApiErrorResponse | null> {
    if (!(error instanceof HttpErrorResponse)) {
      return null;
    }

    const raw: unknown = error.error as unknown;
    if (raw instanceof Blob) {
      try {
        return JSON.parse(await raw.text()) as ApiErrorResponse;
      } catch {
        return null;
      }
    }

    if (raw instanceof ArrayBuffer) {
      try {
        return JSON.parse(new TextDecoder().decode(raw)) as ApiErrorResponse;
      } catch {
        return null;
      }
    }

    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw) as ApiErrorResponse;
      } catch {
        return null;
      }
    }

    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
      return raw as ApiErrorResponse;
    }

    return null;
  }
}


