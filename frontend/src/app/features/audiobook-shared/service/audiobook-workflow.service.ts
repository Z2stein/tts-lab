import { Injectable } from '@angular/core';
import { AudiobookApiService } from './audiobook-api.service';
import {
  AnnotatedSpeakerTurn,
  AudiobookWorkflowCastUpdateRequest,
  AudiobookWorkflowProductionSettingsRequest,
  AudiobookWorkflowSnapshotResponse,
  FinalTtsRequestPreviewResponse,
  GenerateStoryDraftRequest,
  GenerateStoryDraftResponse,
  SingleSpeakerRenderPlanResponse,
  SingleSpeakerRenderRequest,
  SpeakerSplitAnalysisResponse,
  SpeakerSplitTurn,
  SpeakerVoiceAnalysisItem,
  SpeakerVoiceAnalysisResponse
} from '../../../shared/api-contract.generated';
import { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

export type {
  AnnotatedSpeakerTurn,
  AudiobookWorkflowCastUpdateRequest,
  AudiobookWorkflowProductionSettings,
  AudiobookWorkflowProductionSettingsRequest,
  AudiobookWorkflowSnapshotResponse,
  AudiobookWorkflowStage,
  FinalTtsRequestPreviewResponse,
  GenerateStoryDraftRequest,
  GenerateStoryDraftResponse,
  SingleSpeakerRenderPlanResponse,
  SingleSpeakerRenderRequest,
  SpeakerSplitAnalysisResponse,
  SpeakerSplitTurn,
  SpeakerVoiceAnalysisItem,
  SpeakerVoiceAnalysisResponse
} from '../../../shared/api-contract.generated';
export type { CreatedAudioDownload, RequestOptions } from '../../../shared/api-client-types';

@Injectable({ providedIn: 'root' })
export class AudiobookWorkflowService {
  constructor(private readonly audiobookApiService: AudiobookApiService) {}

  async generateStoryDraft(request: GenerateStoryDraftRequest): Promise<GenerateStoryDraftResponse> {
    return this.audiobookApiService.post<GenerateStoryDraftResponse>(
      '/api/audiobooks/workflow/generate-story-draft',
      request,
      'Story draft generation failed'
    );
  }

  async analyzeSpeakers(rawDialogue: string, customHint?: string): Promise<SpeakerVoiceAnalysisResponse> {
    return this.audiobookApiService.post<SpeakerVoiceAnalysisResponse>(
      '/api/audiobooks/workflow/speaker-voice-analysis',
      { rawDialogue, ...(customHint ? { customHint } : {}) },
      'Speaker voice analysis failed'
    );
  }

  async getProjectSnapshot(projectId: string): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.getJsonResponse<AudiobookWorkflowSnapshotResponse>(
      `/api/audiobooks/workflow/projects/${encodeURIComponent(projectId)}`,
      'Audiobook workflow snapshot failed'
    ).then((response) => {
      if (!response.body) {
        throw new Error('Audiobook workflow snapshot failed.');
      }
      return response.body;
    });
  }

  async approveCast(projectId: string): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.post<AudiobookWorkflowSnapshotResponse>(
      `/api/audiobooks/workflow/projects/${encodeURIComponent(projectId)}/cast-approval`,
      undefined,
      'Cast approval failed'
    );
  }

  async saveCast(projectId: string, request: AudiobookWorkflowCastUpdateRequest): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.patchJsonResponse<AudiobookWorkflowSnapshotResponse>(
      `/api/audiobooks/workflow/projects/${encodeURIComponent(projectId)}/cast`,
      request,
      'Cast save failed'
    ).then((response) => {
      if (!response.body) {
        throw new Error('Cast save failed.');
      }
      return response.body;
    });
  }

  async approveScript(projectId: string): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.post<AudiobookWorkflowSnapshotResponse>(
      `/api/audiobooks/workflow/projects/${encodeURIComponent(projectId)}/script-approval`,
      undefined,
      'Script approval failed'
    );
  }

  async saveProductionSettings(projectId: string, request: AudiobookWorkflowProductionSettingsRequest): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.patchJsonResponse<AudiobookWorkflowSnapshotResponse>(
      `/api/audiobooks/workflow/projects/${encodeURIComponent(projectId)}/production-settings`,
      request,
      'Production settings save failed'
    ).then((response) => {
      if (!response.body) {
        throw new Error('Production settings save failed.');
      }
      return response.body;
    });
  }

  async splitDialogue(rawDialogue: string, speakers: SpeakerVoiceAnalysisItem[], projectId: string, customHint?: string): Promise<SpeakerSplitTurn[]> {
    const data = await this.audiobookApiService.post<SpeakerSplitAnalysisResponse>(
      '/api/audiobooks/workflow/speaker-split-analysis',
      { rawDialogue, speakers, projectId, ...(customHint ? { customHint } : {}) },
      'Speaker split analysis failed'
    );
    return data.turns;
  }

  async saveScriptPreview(projectId: string, turns: SpeakerSplitTurn[]): Promise<SpeakerSplitTurn[]> {
    const data = await this.audiobookApiService.post<SpeakerSplitAnalysisResponse>(
      '/api/audiobooks/workflow/script-preview-save',
      { projectId, turns },
      'Script preview save failed'
    );
    return data.turns;
  }

  async annotateEmotions(projectId: string, customHint?: string): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.post<AudiobookWorkflowSnapshotResponse>(
      '/api/audiobooks/workflow/emotion-annotation-analysis',
      { projectId, ...(customHint ? { customHint } : {}) },
      'Emotion annotation analysis failed'
    );
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
      '/api/audiobooks/workflow/final-request-preview',
      request,
      'Final request preview failed'
    );
  }

  async planSingleSpeakerRenderRequests(finalRequest: FinalTtsRequestPreviewResponse): Promise<SingleSpeakerRenderPlanResponse> {
    return this.audiobookApiService.post<SingleSpeakerRenderPlanResponse>(
      '/api/audiobooks/workflow/single-speaker-render-plan',
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

  async markAudioGenerated(projectId: string): Promise<AudiobookWorkflowSnapshotResponse> {
    return this.audiobookApiService.post<AudiobookWorkflowSnapshotResponse>(
      `/api/audiobooks/workflow/projects/${encodeURIComponent(projectId)}/audio-generated`,
      undefined,
      'Audio finalization failed'
    );
  }
}


