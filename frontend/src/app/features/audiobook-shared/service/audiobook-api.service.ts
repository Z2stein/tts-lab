import { Injectable } from '@angular/core';
import { CurrentUserService } from '../../../current-user.service';
import { ApiErrorResponse, SingleSpeakerRenderPlanResponse, SingleSpeakerRenderRequest } from '../../../shared/api-contract.generated';
import { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

export type { SingleSpeakerRenderPlanResponse, SingleSpeakerRenderRequest } from '../../../shared/api-contract.generated';
export type { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

@Injectable({ providedIn: 'root' })
export class AudiobookApiService {
  constructor(private readonly currentUserService: CurrentUserService) {}

  async createAudio(renderPlan: SingleSpeakerRenderPlanResponse, options: RequestOptions = {}, projectId?: string): Promise<CreatedAudioDownload> {
    const url = projectId ? `/api/projects/tts-workbench/create-audio?projectId=${encodeURIComponent(projectId)}` : '/api/projects/tts-workbench/create-audio';
    const response = await this.postResponse(
      url,
      renderPlan,
      'Audio creation failed',
      options
    );

    return {
      blob: await response.blob(),
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
    const response = await this.postResponse(url, body, errorPrefix, options);

    return (await response.json()) as T;
  }

  async postResponse(url: string, body: unknown, errorPrefix: string, options: RequestOptions = {}): Promise<Response> {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
      },
      body: JSON.stringify(body),
      signal: options.signal
    });
    await this.refreshRequestLimits();

    if (!response.ok) {
      const apiError = await this.readApiError(response);
      throw new Error(apiError?.message || `${errorPrefix} (HTTP ${response.status}).`);
    }

    return response;
  }

  private filenameFromContentDisposition(contentDisposition: string | null): string | null {
    if (!contentDisposition) {
      return null;
    }

    const match = /filename="?([^";]+)"?/i.exec(contentDisposition);
    return match ? match[1] : null;
  }

  private async refreshRequestLimits(): Promise<void> {
    const service = this.currentUserService as CurrentUserService & {
      refreshRequestLimits?: () => Promise<unknown>;
    };
    await service.refreshRequestLimits?.();
  }

  private async readApiError(response: Response): Promise<ApiErrorResponse | null> {
    try {
      return (await response.json()) as ApiErrorResponse;
    } catch {
      return null;
    }
  }
}
