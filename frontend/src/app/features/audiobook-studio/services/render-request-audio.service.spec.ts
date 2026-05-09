import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import {
  SingleSpeakerRenderRequest,
  TtsWorkbenchService,
} from '../../tts-workbench/tts-workbench.service';
import { RenderRequestAudioService } from './render-request-audio.service';

describe('RenderRequestAudioService', () => {
  let service: RenderRequestAudioService;
  let tts: jasmine.SpyObj<TtsWorkbenchService>;

  const part0: SingleSpeakerRenderRequest = { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} };
  const part1: SingleSpeakerRenderRequest = { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} };

  beforeEach(() => {
    tts = jasmine.createSpyObj<TtsWorkbenchService>('TtsWorkbenchService', [
      'analyzeSpeakers', 'splitDialogue', 'annotateEmotions',
      'generateFinalJson', 'planSingleSpeakerRenderRequests',
      'createAudio', 'createAudioForRenderRequest',
    ]);
    spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock');
    spyOn(window.URL, 'revokeObjectURL');

    TestBed.configureTestingModule({
      providers: [
        RenderRequestAudioService,
        { provide: TtsWorkbenchService, useValue: tts },
      ],
    });
    service = TestBed.inject(RenderRequestAudioService);
  });

  afterEach(() => service.destroy());

  // ── Successful generation ──────────────────────────────────────────────────

  it('transitions to generated status after a successful download', async () => {
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    await service.generate(part0, 0);
    expect(service.getState(0).status).toBe('generated');
    expect(service.getState(0).blob).not.toBeNull();
    expect(service.getState(0).audioUrl).toBe('blob:mock');
    expect(service.getState(0).error).toBeNull();
  });

  it('calls onPartReady with the request index after success', async () => {
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    const onPartReady = jasmine.createSpy('onPartReady');
    service.onPartReady = onPartReady;
    await service.generate(part0, 0);
    expect(onPartReady).toHaveBeenCalledWith(0);
  });

  // ── Failure ────────────────────────────────────────────────────────────────

  it('transitions to failed status when the download throws', async () => {
    tts.createAudioForRenderRequest.and.rejectWith(new Error('network error'));
    await service.generate(part0, 0);
    expect(service.getState(0).status).toBe('failed');
    expect(service.getState(0).error).toContain('One part failed');
  });

  // ── Cancel ─────────────────────────────────────────────────────────────────

  it('transitions to canceled when cancel() is called while generating', async () => {
    tts.createAudioForRenderRequest.and.callFake((_req, opts) => {
      return new Promise<never>((_res, rej) => {
        opts?.signal?.addEventListener('abort', () => rej(abortError()), { once: true });
      });
    });
    const promise = service.generate(part0, 0);
    expect(service.getState(0).status).toBe('generating');
    service.cancel(0);
    await promise;
    expect(service.getState(0).status).toBe('canceled');
    expect(service.getState(0).error).toBe('Generation canceled. You can retry this part.');
  });

  // ── Timeout ────────────────────────────────────────────────────────────────

  it('transitions to timed-out when partGenerationTimeoutMs elapses', fakeAsync(() => {
    service.partGenerationTimeoutMs = 5;
    tts.createAudioForRenderRequest.and.callFake((_req, opts) => {
      return new Promise<never>((_res, rej) => {
        opts?.signal?.addEventListener('abort', () => rej(abortError()), { once: true });
      });
    });
    void service.generate(part0, 0);
    tick(6);
    flushMicrotasks();
    expect(service.getState(0).status).toBe('timed-out');
    expect(service.getState(0).error).toContain('took too long');
  }));

  // ── Deduplication ──────────────────────────────────────────────────────────

  it('does not start a second generation when the same part is already generating', () => {
    tts.createAudioForRenderRequest.and.callFake((_req, opts) => {
      return new Promise<never>((_res, rej) => {
        opts?.signal?.addEventListener('abort', () => rej(abortError()), { once: true });
      });
    });
    void service.generate(part0, 0);
    void service.generate(part0, 0);
    expect(tts.createAudioForRenderRequest).toHaveBeenCalledTimes(1);
    service.cancel(0);
  });

  // ── anyLoading ─────────────────────────────────────────────────────────────

  it('anyLoading returns true while a part is generating', async () => {
    let resolve!: (v: { blob: Blob; filename: string }) => void;
    tts.createAudioForRenderRequest.and.returnValue(new Promise((r) => (resolve = r)));
    void service.generate(part0, 0);
    expect(service.anyLoading()).toBeTrue();
    resolve({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    await Promise.resolve();
    expect(service.anyLoading()).toBeFalse();
  });

  // ── Count helpers ──────────────────────────────────────────────────────────

  it('generatedCount counts only generated states', async () => {
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    await service.generate(part0, 0);
    service.getState(1, part1).status = 'failed';
    expect(service.generatedCount()).toBe(1);
    expect(service.failedCount()).toBe(1);
  });

  it('missingCount counts indices with no state or not-generated status', () => {
    service.getState(0, part0);
    service.getState(1, part1).status = 'failed';
    expect(service.missingCount(3)).toBe(2); // index 0 (not-generated) + index 2 (no state)
  });

  // ── abortAll ───────────────────────────────────────────────────────────────

  it('abortAll cancels in-flight controllers and clears handles', async () => {
    tts.createAudioForRenderRequest.and.callFake((_req, opts) => {
      return new Promise<never>((_res, rej) => {
        opts?.signal?.addEventListener('abort', () => rej(abortError()), { once: true });
      });
    });
    void service.generate(part0, 0);
    void service.generate(part1, 1);
    service.abortAll();
    await Promise.resolve();
    // After abortAll the in-flight promises should settle (to canceled/timed-out depending on
    // order), but the key invariant is that controllers are cleared.
    expect(service.getState(0).controller).toBeNull();
    expect(service.getState(1).controller).toBeNull();
  });

  // ── revokeUrls ─────────────────────────────────────────────────────────────

  it('revokeUrls revokes all blob URLs and resets audioStates', async () => {
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    await service.generate(part0, 0);
    service.revokeUrls();
    expect(window.URL.revokeObjectURL).toHaveBeenCalled();
    expect(Object.keys(service.audioStates).length).toBe(0);
  });

  // ── Speaker resolution ─────────────────────────────────────────────────────

  it('resolves the speaker name from cast voiceSuggestion when initialising state', () => {
    const cast = [{ speakerName: 'Mara', roleDescription: '', voiceSuggestion: 'Kore' }];
    const state = service.getState(0, part0, cast);
    expect(state.speaker).toBe('Mara');
  });

  it('falls back to the raw voice name when no cast member matches', () => {
    const state = service.getState(0, part0, []);
    expect(state.speaker).toBe('Kore'); // raw name from voice.name
  });

  function abortError(): DOMException {
    return new DOMException('Aborted', 'AbortError');
  }
});
