import { Injectable } from '@angular/core';
import { AudiobookApiService } from './audiobook-api.service';
import {
  AnnotatedSpeakerTurn,
  EmotionAnnotationAnalysisResponse,
  FinalTtsRequestPreviewResponse,
  SingleSpeakerRenderPlanResponse,
  SingleSpeakerRenderRequest,
  SpeakerSplitAnalysisResponse,
  SpeakerSplitTurn,
  SpeakerVoiceAnalysisItem,
  SpeakerVoiceAnalysisResponse
} from '../../../shared/api-contract.generated';
import { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

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
    const data = await this.audiobookApiService.post<SpeakerSplitAnalysisResponse>(
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
  }): Promise<FinalTtsRequestPreviewResponse> {
    return this.audiobookApiService.post<FinalTtsRequestPreviewResponse>(
      '/api/projects/tts-workbench/final-request-preview',
      request,
      'Final request preview failed'
    );
  }

  async planSingleSpeakerRenderRequests(finalRequest: FinalTtsRequestPreviewResponse): Promise<SingleSpeakerRenderPlanResponse> {
    return this.audiobookApiService.post<SingleSpeakerRenderPlanResponse>(
      '/api/projects/tts-workbench/single-speaker-render-plan',
      finalRequest,
      'Single-speaker render plan preview failed'
    );
  }

  async createAudio(renderPlan: SingleSpeakerRenderPlanResponse, options: RequestOptions = {}, projectId?: string): Promise<CreatedAudioDownload> {
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
