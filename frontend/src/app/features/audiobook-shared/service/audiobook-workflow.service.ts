import { Injectable } from '@angular/core';
import {
  AudiobookApiService,
  CreatedAudioDownload,
  RequestOptions,
  SingleSpeakerRenderPlan,
  SingleSpeakerRenderRequest
} from './audiobook-api.service';

export interface SpeakerVoiceAnalysisItem {
  speakerName: string;
  roleDescription: string;
  voiceSuggestion: string;
}

export interface SpeakerVoiceAnalysisResponse {
  speakers: SpeakerVoiceAnalysisItem[];
  projectId: string | null;
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

interface SpeakerSplitAnalysisResponse {
  turns: SpeakerSplitTurn[];
}

interface ScriptPreviewSaveResponse {
  turns: SpeakerSplitTurn[];
}

interface EmotionAnnotationAnalysisResponse {
  turns: AnnotatedSpeakerTurn[];
}

@Injectable({ providedIn: 'root' })
export class AudiobookWorkflowService {
  constructor(private readonly audiobookApiService: AudiobookApiService) {}

  async analyzeSpeakers(rawDialogue: string): Promise<SpeakerVoiceAnalysisResponse> {
    return this.audiobookApiService.post<SpeakerVoiceAnalysisResponse>(
      '/api/projects/tts-workbench/speaker-voice-analysis',
      { rawDialogue },
      'Speaker voice analysis failed'
    );
  }

  async splitDialogue(rawDialogue: string, speakers: SpeakerVoiceAnalysisItem[], projectId: string): Promise<SpeakerSplitTurn[]> {
    const data = await this.audiobookApiService.post<SpeakerSplitAnalysisResponse>(
      '/api/projects/tts-workbench/speaker-split-analysis',
      { rawDialogue, speakers, projectId },
      'Speaker split analysis failed'
    );
    return data.turns;
  }

  async saveScriptPreview(projectId: string, turns: SpeakerSplitTurn[]): Promise<SpeakerSplitTurn[]> {
    const data = await this.audiobookApiService.post<ScriptPreviewSaveResponse>(
      '/api/projects/tts-workbench/script-preview-save',
      { projectId, turns },
      'Script preview save failed'
    );
    return data.turns;
  }

  async annotateEmotions(projectId: string): Promise<AnnotatedSpeakerTurn[]> {
    const data = await this.audiobookApiService.post<EmotionAnnotationAnalysisResponse>(
      '/api/projects/tts-workbench/emotion-annotation-analysis',
      { projectId },
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
    return this.audiobookApiService.post<FinalTtsRequestPreview>(
      '/api/projects/tts-workbench/final-request-preview',
      request,
      'Final request preview failed'
    );
  }

  async planSingleSpeakerRenderRequests(finalRequest: FinalTtsRequestPreview): Promise<SingleSpeakerRenderPlan> {
    return this.audiobookApiService.post<SingleSpeakerRenderPlan>(
      '/api/projects/tts-workbench/single-speaker-render-plan',
      finalRequest,
      'Single-speaker render plan preview failed'
    );
  }

  async createAudio(renderPlan: SingleSpeakerRenderPlan, options: RequestOptions = {}, projectId?: string): Promise<CreatedAudioDownload> {
    return this.audiobookApiService.createAudio(renderPlan, options, projectId);
  }

  async createAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    options: RequestOptions = {},
    projectId?: string
  ): Promise<CreatedAudioDownload> {
    return this.audiobookApiService.createAudioForRenderRequest(renderRequest, options, projectId);
  }
}
