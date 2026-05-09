import { TestBed } from '@angular/core/testing';
import {
  SingleSpeakerRenderRequest,
  TtsWorkbenchService,
} from '../../tts-workbench/tts-workbench.service';
import { FullAudioGenerationService } from './full-audio-generation.service';
import { RenderRequestAudioService } from './render-request-audio.service';

describe('FullAudioGenerationService', () => {
  let service: FullAudioGenerationService;
  let renderSvc: RenderRequestAudioService;
  let tts: jasmine.SpyObj<TtsWorkbenchService>;

  const part = (text: string, voice: string): SingleSpeakerRenderRequest => ({
    input: { text },
    voice: { name: voice },
    audioConfig: {},
  });

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
        FullAudioGenerationService,
        { provide: TtsWorkbenchService, useValue: tts },
      ],
    });
    service = TestBed.inject(FullAudioGenerationService);
    renderSvc = TestBed.inject(RenderRequestAudioService);
  });

  afterEach(() => { service.destroy(); renderSvc.destroy(); });

  // ── Successful full generation ─────────────────────────────────────────────

  it('generates all parts and produces a merged audio URL', async () => {
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    const requests = [part('First', 'Kore'), part('Second', 'Iapetus')];

    await service.generate(requests);

    expect(service.audioUrl).toBe('blob:mock');
    expect(service.filename).toBe('audiobook-preview.mp3');
    expect(service.loading).toBeFalse();
    expect(service.statusMessage).toBe('Audiobook preview is ready.');
  });

  it('calls onAudioReady after the merged blob URL is set', async () => {
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    const onAudioReady = jasmine.createSpy('onAudioReady');
    service.onAudioReady = onAudioReady;

    await service.generate([part('First', 'Kore')]);
    expect(onAudioReady).toHaveBeenCalledTimes(1);
  });

  // ── Skips already-generated parts ─────────────────────────────────────────

  it('skips parts that already have a generated blob', async () => {
    const requests = [part('First', 'Kore'), part('Second', 'Iapetus'), part('Third', 'Kore')];
    // Pre-populate parts 0 and 1
    renderSvc.audioStates[0] = makeGeneratedState();
    renderSvc.audioStates[1] = makeGeneratedState();
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['mp3']), filename: 'p.mp3' });

    await service.generate(requests);
    expect(tts.createAudioForRenderRequest).toHaveBeenCalledTimes(1);
    expect(service.audioUrl).toBe('blob:mock');
  });

  // ── Cancel ─────────────────────────────────────────────────────────────────

  it('stops the loop and sets a canceled status message', async () => {
    const requests = [part('First', 'Kore'), part('Second', 'Iapetus')];

    tts.createAudioForRenderRequest.and.callFake((_req, opts) => {
      return new Promise<never>((_res, rej) => {
        opts?.signal?.addEventListener('abort', () => rej(abortError()), { once: true });
      });
    });

    const run = service.generate(requests);
    service.cancel();
    await run;

    expect(service.loading).toBeFalse();
    expect(service.statusMessage).toBe('Generation canceled. You can retry the pending part.');
    expect(service.audioUrl).toBeNull();
  });

  // ── Partial failure ────────────────────────────────────────────────────────

  it('leaves loading false and does not produce an audioUrl when a part fails', async () => {
    const requests = [part('First', 'Kore'), part('Second', 'Iapetus')];
    tts.createAudioForRenderRequest.and.callFake(async (req) => {
      if ((req.input as { text: string }).text === 'Second') throw new Error('exploded');
      return { blob: new Blob(['mp3']), filename: 'p.mp3' };
    });

    await service.generate(requests);

    expect(service.loading).toBeFalse();
    expect(service.audioUrl).toBeNull();
  });

  // ── Guard: no duplicate runs ───────────────────────────────────────────────

  it('ignores generate() while loading', async () => {
    let resolve!: (v: { blob: Blob; filename: string }) => void;
    tts.createAudioForRenderRequest.and.returnValue(new Promise((r) => (resolve = r)));

    void service.generate([part('Only', 'Kore')]);
    expect(service.loading).toBeTrue();

    void service.generate([part('Only', 'Kore')]);
    expect(tts.createAudioForRenderRequest).toHaveBeenCalledTimes(1);

    resolve({ blob: new Blob(['mp3']), filename: 'p.mp3' });
    await Promise.resolve();
  });

  // ── setAudio / clearAudio ─────────────────────────────────────────────────

  it('setAudio marks stale false and revokes previous URL', () => {
    service.audioUrl = 'blob:old';
    service.stale = true;
    service.setAudio(new Blob(['mp3']), 'preview.mp3');
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:old');
    expect(service.stale).toBeFalse();
    expect(service.audioUrl).toBe('blob:mock');
  });

  it('clearAudio revokes the URL and resets all state', () => {
    service.audioUrl = 'blob:x';
    service.stale = true;
    service.filename = 'preview.mp3';
    service.clearAudio();
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:x');
    expect(service.audioUrl).toBeNull();
    expect(service.stale).toBeFalse();
  });

  function makeGeneratedState() {
    return {
      status: 'generated' as const,
      blob: new Blob(['mp3'], { type: 'audio/mpeg' }),
      audioUrl: 'blob:part',
      filename: 'p.mp3',
      partNumber: 1,
      speaker: null,
      voice: null,
      error: null,
      generatedAt: new Date(),
      startedAt: null,
      requestId: 0,
      timeoutHandle: null,
      controller: null,
      cancelReason: null,
      inFlightPromise: null,
    };
  }

  function abortError(): DOMException {
    return new DOMException('Aborted', 'AbortError');
  }
});
