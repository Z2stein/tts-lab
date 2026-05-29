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

  // Number of automatic retry passes for recoverable part failures (after the initial attempt).
  private readonly maxPartRetries = 3;
  // Delay before each retry pass. Public so tests can disable the wait.
  retryDelayMs = 1000;

  // Track currently running full generation
  private fullGenerationController: AbortController | null = null;
  private currentFullRunId = 0;
  private activePartIndexes = new Set<number>();
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
  async generate(renderRequests: SingleSpeakerRenderRequest[], projectId?: string): Promise<string | null> {
    if (this.loading) return null;

    this.loading = true;
    this.error = null;
    this.statusMessage = null;
    this.stale = false;
    this.currentFullRunId++;
    const runId = this.currentFullRunId;
    this.fullGenerationController = new AbortController();
    const signal = this.fullGenerationController.signal;

    let fallbackProjectId: string | null = projectId ?? null;

    try {
      for (let attempt = 0; attempt <= this.maxPartRetries; attempt++) {
        // Attempt 0 triggers every incomplete part; retries only re-trigger recoverable failures.
        const partsToTrigger = renderRequests
          .map((request, index) => ({ index, request }))
          .filter(({ index, request }) => {
            const status = this.renderRequestAudioService.getState(index, request).status;
            if (status === 'generated' || status === 'generating') return false;
            // We consider generating parts as valid because we will await them later.
            return attempt === 0 || status === 'failed' || status === 'timed-out';
          });

        if (partsToTrigger.length > 0) {
          partsToTrigger.forEach(({ index }) => this.activePartIndexes.add(index));
          this.statusMessage = attempt === 0
            ? `Preparing ${partsToTrigger.length} incomplete ${partsToTrigger.length === 1 ? 'part' : 'parts'}...`
            : `Retrying ${partsToTrigger.length} failed ${partsToTrigger.length === 1 ? 'part' : 'parts'} — attempt ${attempt + 1} of ${this.maxPartRetries + 1}...`;
          const generationPromises = partsToTrigger.map(({ index, request }) =>
            this.renderRequestAudioService.generate(request, index, { fullRunId: runId, projectId })
              .catch(() => undefined) // Local orchestration catches failures via getState later
          );
          const projectIds = await this.awaitWithAbort(Promise.all(generationPromises), signal);
          if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
          if (!fallbackProjectId) {
            fallbackProjectId = projectIds.find((id) => id !== undefined) ?? null;
          }
        }

        this.statusMessage = 'Waiting for all parts to finish...';
        const pendingPromises = renderRequests
          .map((_, idx) => this.renderRequestAudioService.getState(idx).inFlightPromise)
          .filter((p) => p !== null);

        if (pendingPromises.length > 0) {
          const remainingProjectIds = await this.awaitWithAbort(Promise.all(pendingPromises), signal);
          if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
          if (!fallbackProjectId) {
            fallbackProjectId = remainingProjectIds.find((id) => id !== undefined) ?? null;
          }
        }

        if (signal.aborted) throw new DOMException('Aborted', 'AbortError');

        // Verify success
        const finalStates = renderRequests.map((_, idx) => this.renderRequestAudioService.getState(idx));
        const failures = finalStates.filter(s => s.status !== 'generated');

        if (failures.length === 0) break;

        const recoverable = failures.filter(s => s.status === 'failed' || s.status === 'timed-out');
        if (recoverable.length === 0 || attempt === this.maxPartRetries) {
          this.error = `${failures.length} ${failures.length === 1 ? 'part' : 'parts'} failed to generate. Please retry them individually before generating the final preview.`;
          return null; // Return null on failure instead of throwing
        }

        await this.delay(this.retryDelayMs, signal);
        if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
      }

      // Concat everything
      this.statusMessage = 'Merging audio parts...';
      const blobs = renderRequests
        .map((_, idx) => this.renderRequestAudioService.getState(idx).blob)
        .filter((b): b is Blob => b !== null);

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
        this.activePartIndexes.clear();
        if (this.statusMessage === 'Waiting for all parts to finish...' || this.statusMessage === 'Merging audio parts...') {
          this.statusMessage = null;
        }
      }
    }
  }

  cancel(): void {
    this.activePartIndexes.forEach((index) => this.renderRequestAudioService.cancel(index));
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

  private delay(ms: number, signal: AbortSignal): Promise<void> {
    if (ms <= 0) return Promise.resolve();
    return new Promise<void>((resolve, reject) => {
      const handle = window.setTimeout(resolve, ms);
      signal.addEventListener('abort', () => {
        window.clearTimeout(handle);
        reject(new DOMException('Aborted', 'AbortError'));
      }, { once: true });
    });
  }

  private async awaitWithAbort<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
    if (signal.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }

    return new Promise<T>((resolve, reject) => {
      const onAbort = () => reject(new DOMException('Aborted', 'AbortError'));
      signal.addEventListener('abort', onAbort, { once: true });
      promise.then(
        (value) => {
          signal.removeEventListener('abort', onAbort);
          resolve(value);
        },
        (error) => {
          signal.removeEventListener('abort', onAbort);
          reject(error);
        }
      );
    });
  }
}
