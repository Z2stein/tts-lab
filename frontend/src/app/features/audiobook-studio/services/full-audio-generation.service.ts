import { Injectable } from '@angular/core';
import { SingleSpeakerRenderRequest } from '../../audiobook-shared/service/audiobook-api.service';
import { RenderRequestAudioService } from '../../audiobook-shared/service/render-request-audio.service';

@Injectable()
export class FullAudioGenerationService {
  loading = false;
  error: string | null = null;
  audioUrl: string | null = null;
  filename: string | null = null;
  stale = false;
  statusMessage: string | null = null;

  // Track currently running full generation
  private fullGenerationController: AbortController | null = null;
  private currentFullRunId = 0;
  // Fallback blob tracking specifically for the merged download
  private fullPlanBlob: Blob | null = null;

  constructor(private readonly renderRequestAudioService: RenderRequestAudioService) {}

  /**
   * Orchestrates full audiobook generation:
   * 1. Finds all render requests without valid parts (not-generated, failed, canceled, timed-out)
   *    and triggers their generation individually via RenderRequestAudioService.
   * 2. Waits for all generations to finish (both existing and newly started).
   * 3. Concatenates all resulting blobs into a single continuous MP3 preview.
   *
   * Only incomplete parts are generated over the network. If all parts are already downloaded,
   * this operation is entirely local and instantaneous.
   */
  async generate(renderRequests: SingleSpeakerRenderRequest[]): Promise<string | null> {
    if (this.loading) return null;

    this.loading = true;
    this.error = null;
    this.statusMessage = null;
    this.stale = false;
    this.currentFullRunId++;
    const runId = this.currentFullRunId;
    this.fullGenerationController = new AbortController();
    const signal = this.fullGenerationController.signal;

    let fallbackProjectId: string | null = null;

    try {
      const partsToTrigger: { index: number; request: SingleSpeakerRenderRequest }[] = [];

      renderRequests.forEach((req, idx) => {
        const state = this.renderRequestAudioService.getState(idx, req);
        // We consider generating parts as valid because we will await them later.
        if (state.status !== 'generated' && state.status !== 'generating') {
          partsToTrigger.push({ index: idx, request: req });
        }
      });

      if (partsToTrigger.length > 0) {
        this.statusMessage = `Preparing ${partsToTrigger.length} incomplete ${partsToTrigger.length === 1 ? 'part' : 'parts'}...`;
        const generationPromises = partsToTrigger.map(({ index, request }) =>
          this.renderRequestAudioService.generate(request, index, { fullRunId: runId })
            .catch(() => undefined) // Local orchestration catches failures via getState later
        );
        const projectIds = await Promise.all(generationPromises);
        if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
        fallbackProjectId = projectIds.find((id) => id !== undefined) ?? null;
      }

      this.statusMessage = 'Waiting for all parts to finish...';
      const pendingPromises = renderRequests
        .map((_, idx) => this.renderRequestAudioService.getState(idx).inFlightPromise)
        .filter((p) => p !== null);

      if (pendingPromises.length > 0) {
        const remainingProjectIds = await Promise.all(pendingPromises);
        if (!fallbackProjectId) {
          fallbackProjectId = remainingProjectIds.find((id) => id !== undefined) ?? null;
        }
      }

      if (signal.aborted) throw new DOMException('Aborted', 'AbortError');

      // Verify success
      const finalStates = renderRequests.map((_, idx) => this.renderRequestAudioService.getState(idx));
      const failures = finalStates.filter(s => s.status !== 'generated');

      if (failures.length > 0) {
        this.error = `${failures.length} ${failures.length === 1 ? 'part' : 'parts'} failed to generate. Please retry them individually before generating the final preview.`;
        return null; // Return null on failure instead of throwing
      }

      // Concat everything
      this.statusMessage = 'Merging audio parts...';
      const blobs = finalStates.map(s => s.blob).filter((b): b is Blob => b !== null);

      if (blobs.length > 0) {
        if (this.audioUrl) {
          window.URL.revokeObjectURL(this.audioUrl);
        }
        this.fullPlanBlob = new Blob(blobs, { type: 'audio/mpeg' });
        this.audioUrl = window.URL.createObjectURL(this.fullPlanBlob);
        this.filename = 'audiobook-preview-merged.mp3';
      }

      this.statusMessage = null;
      return fallbackProjectId;

    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        this.error = 'Audiobook preview generation was canceled.';
      } else {
        this.error = e instanceof Error ? e.message : 'Unknown generation error';
      }
      return null;
    } finally {
      if (this.currentFullRunId === runId) {
        this.loading = false;
        this.fullGenerationController = null;
        if (this.statusMessage === 'Waiting for all parts to finish...' || this.statusMessage === 'Merging audio parts...') {
          this.statusMessage = null;
        }
      }
    }
  }

  cancel(): void {
    if (this.fullGenerationController) {
      this.fullGenerationController.abort();
      this.fullGenerationController = null;
    }
  }

  clearAudio(): void {
    if (this.audioUrl) {
      window.URL.revokeObjectURL(this.audioUrl);
      this.audioUrl = null;
    }
    this.filename = null;
    this.fullPlanBlob = null;
    this.stale = false;
    this.error = null;
    this.statusMessage = null;
  }

  markStale(message: string): void {
    this.stale = true;
    if (this.audioUrl) {
      this.statusMessage = message;
    }
  }
}
