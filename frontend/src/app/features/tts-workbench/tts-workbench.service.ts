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

  private async post<T>(url: string, body: unknown, errorPrefix: string): Promise<T> {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
      },
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      const apiError = await this.readApiError(response);
      throw new Error(apiError?.message || `${errorPrefix} (HTTP ${response.status}).`);
    }

    return (await response.json()) as T;
  }

  private async readApiError(response: Response): Promise<ApiErrorResponse | null> {
    try {
      return (await response.json()) as ApiErrorResponse;
    } catch {
      return null;
    }
  }
}
