import { Injectable } from '@angular/core';
import {
  AudiobookApiService,
  SingleSpeakerRenderRequest,
} from './audiobook-api.service';
import { SpeakerVoiceAnalysisItem } from './audiobook-workflow.service';
import { RenderRequestAudioState } from '../../audiobook-studio/models/audiobook-studio.types';

export interface GenerateOptions {
  fullRunId?: number;
  projectId?: string;
}

@Injectable()
export class RenderRequestAudioService {
  partGenerationTimeoutMs = 120_000;
  audioStates: Record<number, RenderRequestAudioState> = {};

  /** Called after each part's blob URL is created. Use to trigger WaveSurfer re-init in the page. */
  onPartReady: ((requestIndex: number) => void) | null = null;

  // Increments every second while any part is generating — keeps zone.js CD running.
  clockTick = 0;
  private clockHandle: number | null = null;
  private generationCounter = 0;
  private lastProjectId: string | undefined;

  constructor(private readonly audiobookApiService: AudiobookApiService) {}

  getState(
    requestIndex: number,
    renderRequest?: SingleSpeakerRenderRequest,
    cast: readonly SpeakerVoiceAnalysisItem[] = []
  ): RenderRequestAudioState {
    if (!this.audioStates[requestIndex]) {
      this.audioStates[requestIndex] = {
        status: 'not-generated',
        partNumber: requestIndex + 1,
        speaker: renderRequest ? this.extractSpeaker(renderRequest, cast) : null,
        voice: renderRequest ? this.extractVoice(renderRequest) : null,
        blob: null,
        error: null,
        audioUrl: null,
        filename: null,
        generatedAt: null,
        startedAt: null,
        requestId: 0,
        timeoutHandle: null,
        controller: null,
        cancelReason: null,
        inFlightPromise: null,
      };
    }
    return this.audioStates[requestIndex];
  }

  async generate(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    options: GenerateOptions = {}
  ): Promise<string | undefined> {
    const state = this.getState(requestIndex, renderRequest);
    if (state.status === 'generating' && state.inFlightPromise) {
      return state.inFlightPromise.then(() => this.lastProjectId);
    }

    const requestId = ++this.generationCounter;
    const controller = new AbortController();
    const timeoutHandle = window.setTimeout(() => {
      const activeState = this.audioStates[requestIndex];
      if (!activeState || activeState.requestId !== requestId || activeState.status !== 'generating') {
        return;
      }
      activeState.cancelReason = 'timeout';
      activeState.controller?.abort();
    }, this.partGenerationTimeoutMs);

    state.status = 'generating';
    state.error = null;
    state.startedAt = Date.now();
    state.requestId = requestId;
    state.timeoutHandle = timeoutHandle;
    state.controller = controller;
    state.cancelReason = null;
    const promise = this.run(renderRequest, requestIndex, requestId, controller, options.fullRunId, options.projectId);
    state.inFlightPromise = promise;
    this.startClock();
    return promise.then(() => this.lastProjectId);
  }

  cancel(requestIndex: number): void {
    const state = this.audioStates[requestIndex];
    if (!state || state.status !== 'generating') return;
    state.cancelReason = 'cancel';
    state.controller?.abort();
  }

  anyLoading(): boolean {
    return Object.values(this.audioStates).some((s) => s.status === 'generating');
  }

  generatedCount(): number {
    return Object.values(this.audioStates).filter(
      (s) => s.status === 'generated' || (s.blob !== null && s.status !== 'generating')
    ).length;
  }

  failedCount(): number {
    return Object.values(this.audioStates).filter((s) => s.status === 'failed').length;
  }

  canceledCount(): number {
    return Object.values(this.audioStates).filter((s) => s.status === 'canceled').length;
  }

  timedOutCount(): number {
    return Object.values(this.audioStates).filter((s) => s.status === 'timed-out').length;
  }

  missingCount(total: number): number {
    let count = 0;
    for (let i = 0; i < total; i++) {
      const state = this.audioStates[i];
      if (!state || state.status === 'not-generated') count++;
    }
    return count;
  }

  abortAll(): void {
    Object.values(this.audioStates).forEach((state) => {
      state.controller?.abort();
      if (state.timeoutHandle !== null) {
        window.clearTimeout(state.timeoutHandle);
      }
      state.controller = null;
      state.timeoutHandle = null;
      state.startedAt = null;
      state.inFlightPromise = null;
    });
  }

  revokeUrls(): void {
    Object.values(this.audioStates).forEach((state) => {
      if (state.audioUrl) {
        window.URL.revokeObjectURL(state.audioUrl);
      }
    });
    this.audioStates = {};
    this.stopClock();
  }

  destroy(): void {
    this.abortAll();
    this.revokeUrls();
  }

  private async run(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    requestId: number,
    controller: AbortController,
    fullRunId?: number,
    projectId?: string
  ): Promise<void> {
    const state = this.audioStates[requestIndex];
    try {
      const download = await this.audiobookApiService.createAudioForRenderRequest(renderRequest, {
        signal: controller.signal,
      }, projectId);
      if (!this.isCurrent(requestIndex, requestId)) return;

      // Capture projectId from first generation if not already set
      if (download.projectId && !this.lastProjectId) {
        this.lastProjectId = download.projectId;
      }

      this.setAudio(requestIndex, download.blob, `tts-audio-part-${requestIndex + 1}.mp3`);
      state.status = 'generated';
      state.error = null;
      state.cancelReason = null;
      this.onPartReady?.(requestIndex);
    } catch (error) {
      if (!this.isCurrent(requestIndex, requestId)) return;

      if (error instanceof DOMException && error.name === 'AbortError') {
        if (state.cancelReason === 'timeout') {
          state.status = 'timed-out';
          state.error = 'This part took too long and was stopped. Try again or edit the text.';
        } else {
          state.status = 'canceled';
          state.error = 'Generation canceled. You can retry this part.';
        }
      } else {
        state.status = 'failed';
        state.error = 'One part failed. Other generated parts are still available.';
      }
    } finally {
      if (this.isCurrent(requestIndex, requestId)) {
        this.clearGeneration(requestIndex, requestId);
      }
      this.stopClockIfIdle();
    }
  }

  private setAudio(requestIndex: number, blob: Blob, filename: string): void {
    const state = this.audioStates[requestIndex];
    if (!state) return;
    if (state.audioUrl) {
      window.URL.revokeObjectURL(state.audioUrl);
    }
    state.blob = blob;
    state.audioUrl = window.URL.createObjectURL(blob);
    state.filename = filename;
    state.generatedAt = new Date();
  }

  private clearGeneration(requestIndex: number, requestId: number): void {
    const state = this.audioStates[requestIndex];
    if (!state || state.requestId !== requestId) return;
    if (state.timeoutHandle !== null) {
      window.clearTimeout(state.timeoutHandle);
    }
    state.controller = null;
    state.timeoutHandle = null;
    state.startedAt = null;
    state.inFlightPromise = null;
  }

  private isCurrent(requestIndex: number, requestId: number): boolean {
    return this.audioStates[requestIndex]?.requestId === requestId;
  }

  private startClock(): void {
    if (this.clockHandle !== null) return;
    this.clockHandle = window.setInterval(() => { this.clockTick++; }, 1000);
  }

  private stopClock(): void {
    if (this.clockHandle !== null) {
      window.clearInterval(this.clockHandle);
      this.clockHandle = null;
    }
  }

  private stopClockIfIdle(): void {
    if (!this.anyLoading()) {
      this.stopClock();
    }
  }

  private extractSpeaker(
    renderRequest: SingleSpeakerRenderRequest,
    cast: readonly SpeakerVoiceAnalysisItem[]
  ): string | null {
    const voice = this.asRecord(renderRequest.voice);
    const raw = voice?.['speakerName'] ?? voice?.['speaker'] ?? voice?.['name'];
    if (typeof raw !== 'string' || !raw.trim()) return null;
    return this.castSpeakerForVoice(raw, cast) ?? raw;
  }

  private extractVoice(renderRequest: SingleSpeakerRenderRequest): string | null {
    const voice = this.asRecord(renderRequest.voice);
    const name = voice?.['name'] ?? voice?.['voiceName'];
    return typeof name === 'string' && name.trim() ? name : null;
  }

  private castSpeakerForVoice(
    voiceName: string,
    cast: readonly SpeakerVoiceAnalysisItem[]
  ): string | null {
    const key = voiceName.trim().toLowerCase();
    return cast.find((s) => s.voiceSuggestion?.trim().toLowerCase() === key)?.speakerName ?? null;
  }

  private asRecord(value: unknown): Record<string, unknown> | null {
    return value !== null && typeof value === 'object' && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : null;
  }
}
