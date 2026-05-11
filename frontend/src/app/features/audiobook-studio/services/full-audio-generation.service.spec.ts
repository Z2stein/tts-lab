import { TestBed } from '@angular/core/testing';
import {
  SingleSpeakerRenderRequest,
} from '../../audiobook-shared/service/audiobook-api.service';
import { FullAudioGenerationService } from './full-audio-generation.service';
import { RenderRequestAudioService } from '../../audiobook-shared/service/render-request-audio.service';
import { AudiobookApiService } from '../../audiobook-shared/service/audiobook-api.service';

describe('FullAudioGenerationService', () => {
  let service: FullAudioGenerationService;
  let renderSvc: RenderRequestAudioService;
  let tts: jasmine.SpyObj<AudiobookApiService>;

  const part = (text: string, voice: string): SingleSpeakerRenderRequest => ({
    input: { text },
    voice: { name: voice },
    audioConfig: {},
  });

  beforeEach(() => {
    tts = jasmine.createSpyObj<AudiobookApiService>('AudiobookApiService', [
      'createAudio', 'createAudioForRenderRequest', 'post', 'postResponse'
    ]);
    spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock-full');
    spyOn(window.URL, 'revokeObjectURL');

    TestBed.configureTestingModule({
      providers: [
        FullAudioGenerationService,
        RenderRequestAudioService,
        { provide: AudiobookApiService, useValue: tts },
      ],
    });
    service = TestBed.inject(FullAudioGenerationService);
    renderSvc = TestBed.inject(RenderRequestAudioService);
  });

  afterEach(() => {
    service.clearAudio();
    renderSvc.destroy();
  });

  it('generates missing parts then concatenates them', async () => {
    const p0 = part('One', 'V1');
    const p1 = part('Two', 'V2');

    // Make part0 already generated.
    renderSvc.getState(0, p0).status = 'generated';
    renderSvc.getState(0, p0).blob = new Blob(['p0']);

    // Make part1 not generated. Mock the TTS API to resolve it.
    tts.createAudioForRenderRequest.and.resolveTo({ blob: new Blob(['p1']), filename: 'p1.mp3' });

    await service.generate([p0, p1]);

    expect(tts.createAudioForRenderRequest).toHaveBeenCalledTimes(1); // Only for part 1
    expect(service.loading).toBeFalse();
    expect(service.error).toBeNull();
    expect(service.audioUrl).toBe('blob:mock-full');
    expect(service.filename).toBe('audiobook-preview-merged.mp3');
  });

  it('sets error if any part fails to generate', async () => {
    const p0 = part('One', 'V1');

    tts.createAudioForRenderRequest.and.rejectWith(new Error('Network error'));

    await service.generate([p0]);

    expect(service.loading).toBeFalse();
    expect(service.error).toContain('1 part failed to generate');
    expect(service.audioUrl).toBeNull();
  });

  it('cancels orchestrating when cancel() is called', async () => {
    const p0 = part('One', 'V1');

    // Hang the API call so generate() blocks.
    let resolveApi!: () => void;
    tts.createAudioForRenderRequest.and.returnValue(new Promise<any>(r => resolveApi = r));

    const genPromise = service.generate([p0]);
    expect(service.loading).toBeTrue();

    service.cancel();

    // Since we canceled the *orchestrator*, the overall generate promise finishes early.
    // The individual RenderRequestAudioService part might still be running unless also aborted,
    // but the full generation service should surface the orchestrator abort.
    await genPromise;

    expect(service.error).toBe('Audiobook preview generation was canceled.');
    expect(service.loading).toBeFalse();
  });

  it('markStale sets stale flag and preserves URL', () => {
    service.audioUrl = 'blob:existing';
    service.markStale('Test stale message');
    expect(service.stale).toBeTrue();
    expect(service.audioUrl).toBe('blob:existing'); // keeps URL
    expect(service.statusMessage).toBe('Test stale message');
  });

  it('clearAudio resets everything', () => {
    service.audioUrl = 'blob:old';
    service.filename = 'old.mp3';
    service.error = 'err';
    service.stale = true;

    service.clearAudio();

    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:old');
    expect(service.audioUrl).toBeNull();
    expect(service.filename).toBeNull();
    expect(service.error).toBeNull();
    expect(service.stale).toBeFalse();
  });
});
