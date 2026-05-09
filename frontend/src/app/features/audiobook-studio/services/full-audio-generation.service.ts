import { Injectable } from '@angular/core';
import { SingleSpeakerRenderRequest } from '../../tts-workbench/tts-workbench.service';
import { RenderRequestAudioService } from './render-request-audio.service';

@Injectable()
export class FullAudioGenerationService {
  loading = false;
  error: string | null = null;
  stale = false;
  statusMessage: string | null = null;
  audioUrl: string | null = null;
  filename: string | null = null;

  /** Called after the merged blob URL is created. Use to trigger full-waveform re-init. */
  onAudioReady: (() => void) | null = null;

  private runId = 0;
  private canceled = false;
  private projectId: string | null = null;
  activeRequestIndex: number | null = null;

  constructor(private readonly renderRequestAudioService: RenderRequestAudioService) {}

  getProjectId(): string | null {
    return this.projectId;
  }

  async generate(renderRequests: SingleSpeakerRenderRequest[]): Promise<string | null> {
    if (this.loading || this.renderRequestAudioService.anyLoading()) {
      return null;
    }

    const runId = ++this.runId;
    this.canceled = false;
    this.activeRequestIndex = null;
    this.loading = true;
    this.error = null;
    this.stale = this.audioUrl !== null;
    this.statusMessage = this.audioUrl
      ? 'Rebuilding the audiobook preview. Existing audio stays available until the new preview is ready.'
      : 'Building the audiobook preview from the generated parts.';

    try {
      for (const [requestIndex, renderRequest] of renderRequests.entries()) {
        if (runId !== this.runId || this.canceled) break;

        const state = this.renderRequestAudioService.audioStates[requestIndex];
        if (state?.status === 'generated' && state.blob) continue;

        this.activeRequestIndex = requestIndex;
        const returnedProjectId = await this.renderRequestAudioService.generate(renderRequest, requestIndex, { fullRunId: runId, projectId: this.projectId || undefined });

        // Capture projectId from first generation
        if (!this.projectId && returnedProjectId) {
          this.projectId = returnedProjectId;
        }
        this.activeRequestIndex = null;
      }

      if (runId !== this.runId) return this.projectId;

      if (this.canceled) {
        this.loading = false;
        this.statusMessage = 'Generation canceled. You can retry the pending part.';
        return this.projectId;
      }

      const incompleteParts = renderRequests
        .map((_, i) => this.renderRequestAudioService.audioStates[i])
        .filter((s) => !s || s.status !== 'generated' || !s.blob);

      if (incompleteParts.length > 0) {
        this.loading = false;
        this.statusMessage = null;
        return this.projectId;
      }

      const audioParts = renderRequests.map(
        (_, i) => this.renderRequestAudioService.audioStates[i].blob as Blob
      );
      this.setAudio(new Blob(audioParts, { type: 'audio/mpeg' }), 'audiobook-preview.mp3');
      this.statusMessage = 'Audiobook preview is ready.';
      return this.projectId;
    } catch {
      if (!this.canceled) {
        this.error = 'One audio part could not be generated. The other parts are still available. You can retry this part or edit the text.';
      }
      return this.projectId;
    } finally {
      if (runId === this.runId) {
        this.loading = false;
        this.activeRequestIndex = null;
      }
    }
  }

  cancel(): void {
    if (!this.loading) return;
    this.canceled = true;
    const activeIndex = this.activeRequestIndex;
    if (activeIndex !== null) {
      this.renderRequestAudioService.cancel(activeIndex);
    }
    this.loading = false;
    this.statusMessage = 'Generation canceled. You can retry the pending part.';
  }

  setAudio(blob: Blob, filename: string): void {
    if (this.audioUrl) {
      window.URL.revokeObjectURL(this.audioUrl);
    }
    this.audioUrl = window.URL.createObjectURL(blob);
    this.filename = filename;
    this.stale = false;
    this.onAudioReady?.();
  }

  markStale(message: string): void {
    this.stale = true;
    this.statusMessage = message;
  }

  clearAudio(): void {
    if (this.audioUrl) {
      window.URL.revokeObjectURL(this.audioUrl);
    }
    this.audioUrl = null;
    this.filename = null;
    this.stale = false;
    this.statusMessage = null;
    this.loading = false;
    this.error = null;
    this.activeRequestIndex = null;
    this.canceled = false;
    this.projectId = null;
  }

  destroy(): void {
    this.cancel();
    this.clearAudio();
  }
}
