import { Injectable } from '@angular/core';
import { CurrentUserService } from '../../current-user.service';

export interface SpeakerVoiceAnalysisItem {
  speakerName: string;
  roleDescription: string;
  voiceSuggestion: string;
}

export interface SpeakerSplitTurn {
  speaker: string;
  text: string;
}

export interface AnnotatedSpeakerTurn {
  speaker: string;
  text: string;
}

export interface FinalTtsRequestPreview {
  input: unknown;
  voice: unknown;
  audioConfig: unknown;
}

export interface SingleSpeakerRenderRequest {
  input: unknown;
  voice: unknown;
  audioConfig: unknown;
}

export interface SingleSpeakerRenderPlan {
  renderRequests: SingleSpeakerRenderRequest[];
}

export interface CreatedAudioDownload {
  blob: Blob;
  filename: string;
}

interface SpeakerVoiceAnalysisResponse {
  speakers: SpeakerVoiceAnalysisItem[];
}

interface SpeakerSplitAnalysisResponse {
  turns: SpeakerSplitTurn[];
}

interface EmotionAnnotationAnalysisResponse {
  turns: AnnotatedSpeakerTurn[];
}

interface ApiErrorResponse {
  status?: number;
  code?: string;
  message?: string;
  details?: string | null;
  requestId?: string;
}

@Injectable({ providedIn: 'root' })
export class TtsWorkbenchService {
  constructor(private readonly currentUserService: CurrentUserService) {}

  async analyzeSpeakers(rawDialogue: string): Promise<SpeakerVoiceAnalysisItem[]> {
    const data = await this.post<SpeakerVoiceAnalysisResponse>(
      '/api/projects/tts-workbench/speaker-voice-analysis',
      { rawDialogue },
      'Speaker voice analysis failed'
    );
    return data.speakers;
  }

  async splitDialogue(rawDialogue: string, speakers: SpeakerVoiceAnalysisItem[]): Promise<SpeakerSplitTurn[]> {
    const data = await this.post<SpeakerSplitAnalysisResponse>(
      '/api/projects/tts-workbench/speaker-split-analysis',
      { rawDialogue, speakers },
      'Speaker split analysis failed'
    );
    return data.turns;
  }

  async annotateEmotions(turns: SpeakerSplitTurn[]): Promise<AnnotatedSpeakerTurn[]> {
    const data = await this.post<EmotionAnnotationAnalysisResponse>(
      '/api/projects/tts-workbench/emotion-annotation-analysis',
      { turns },
      'Emotion annotation analysis failed'
    );
    return data.turns;
  }

  async generateFinalJson(request: {
    prompt: string;
    speakers: SpeakerVoiceAnalysisItem[];
    annotatedTurns: AnnotatedSpeakerTurn[];
    languageCode: string;
    modelName: string;
    audioEncoding: string;
  }): Promise<FinalTtsRequestPreview> {
    return this.post<FinalTtsRequestPreview>(
      '/api/projects/tts-workbench/final-request-preview',
      request,
      'Final request preview failed'
    );
  }

  async planSingleSpeakerRenderRequests(finalRequest: FinalTtsRequestPreview): Promise<SingleSpeakerRenderPlan> {
    return this.post<SingleSpeakerRenderPlan>(
      '/api/projects/tts-workbench/single-speaker-render-plan',
      finalRequest,
      'Single-speaker render plan preview failed'
    );
  }

  async createAudio(renderPlan: SingleSpeakerRenderPlan): Promise<CreatedAudioDownload> {
    const response = await this.postResponse(
      '/api/projects/tts-workbench/create-audio',
      renderPlan,
      'Audio creation failed'
    );

    return {
      blob: await response.blob(),
      filename: this.filenameFromContentDisposition(response.headers.get('Content-Disposition')) || 'tts-render-request-1.mp3'
    };
  }


  async createAudioForRenderRequest(renderRequest: SingleSpeakerRenderRequest): Promise<CreatedAudioDownload> {
    return this.createAudio({ renderRequests: [renderRequest] });
  }

  private async post<T>(url: string, body: unknown, errorPrefix: string): Promise<T> {
    const response = await this.postResponse(url, body, errorPrefix);

    return (await response.json()) as T;
  }

  private async postResponse(url: string, body: unknown, errorPrefix: string): Promise<Response> {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
      },
      body: JSON.stringify(body)
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
