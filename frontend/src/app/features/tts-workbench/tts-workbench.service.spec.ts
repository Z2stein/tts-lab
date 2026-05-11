import { TestBed } from '@angular/core/testing';
import { AudiobookApiService } from '../audiobook-shared/service/audiobook-api.service';
import { TtsWorkbenchService } from './tts-workbench.service';

describe('TtsWorkbenchService', () => {
  let service: TtsWorkbenchService;
  let audiobookApiServiceSpy: jasmine.SpyObj<AudiobookApiService>;

  beforeEach(() => {
    audiobookApiServiceSpy = jasmine.createSpyObj<AudiobookApiService>('AudiobookApiService', [
      'post', 'createAudio', 'createAudioForRenderRequest'
    ]);

    TestBed.configureTestingModule({
      providers: [
        TtsWorkbenchService,
        { provide: AudiobookApiService, useValue: audiobookApiServiceSpy }
      ]
    });
    service = TestBed.inject(TtsWorkbenchService);
  });

  it('posts raw dialogue to the speaker voice analysis endpoint', async () => {
    audiobookApiServiceSpy.post.and.resolveTo({
      speakers: [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm voice' }]
    });

    const speakers = await service.analyzeSpeakers('Alice: Hello');

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/speaker-voice-analysis',
      { rawDialogue: 'Alice: Hello' },
      'Speaker voice analysis failed'
    );
    expect(speakers[0].speakerName).toBe('Alice');
  });

  it('posts dialogue and speakers to the speaker split endpoint', async () => {
    audiobookApiServiceSpy.post.and.resolveTo({
      turns: [{ speaker: 'Alice', text: 'Hello' }]
    });

    const speakers = [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm voice' }];
    const turns = await service.splitDialogue('Alice: Hello', speakers);

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/speaker-split-analysis',
      { rawDialogue: 'Alice: Hello', speakers },
      'Speaker split analysis failed'
    );
    expect(turns[0].text).toBe('Hello');
  });

  it('posts turns to the emotion annotation endpoint', async () => {
    audiobookApiServiceSpy.post.and.resolveTo({
      turns: [{ speaker: 'Alice', text: '[urgent] Hello!' }]
    });

    const turns = await service.annotateEmotions([{ speaker: 'Alice', text: 'Hello!' }]);

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/emotion-annotation-analysis',
      { turns: [{ speaker: 'Alice', text: 'Hello!' }] },
      'Emotion annotation analysis failed'
    );
    expect(turns[0].text).toBe('[urgent] Hello!');
  });

  it('posts final preview data to the final request endpoint', async () => {
    audiobookApiServiceSpy.post.and.resolveTo({
      input: { prompt: 'Prompt' },
      voice: { languageCode: 'en-US' },
      audioConfig: { audioEncoding: 'MP3' }
    });

    const request = {
      prompt: 'Prompt',
      speakers: [],
      annotatedTurns: [],
      languageCode: 'en-US',
      modelName: '{{google-model}}',
      audioEncoding: 'MP3'
    };
    const finalJson = await service.generateFinalJson(request);

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/final-request-preview',
      request,
      'Final request preview failed'
    );
    expect((finalJson.audioConfig as any).audioEncoding).toBe('MP3');
  });

  it('posts final request JSON to the single-speaker render plan endpoint', async () => {
    audiobookApiServiceSpy.post.and.resolveTo({
      renderRequests: [{
        input: { text: 'Hello' },
        voice: { languageCode: 'en-US', name: 'Kore', modelName: '{{google-model}}' },
        audioConfig: { audioEncoding: 'MP3' }
      }]
    });

    const request = {
      input: { prompt: 'Prompt' },
      voice: { languageCode: 'en-US' },
      audioConfig: { audioEncoding: 'MP3' }
    };
    const plan = await service.planSingleSpeakerRenderRequests(request);

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/single-speaker-render-plan',
      request,
      'Single-speaker render plan preview failed'
    );
    expect((plan.renderRequests[0].voice as any).name).toBe('Kore');
  });

  it('delegates createAudio to AudiobookApiService', async () => {
    const download = { blob: new Blob(['mp3']), filename: 'tts-render-request-1.mp3' };
    audiobookApiServiceSpy.createAudio.and.resolveTo(download);

    const renderPlan = {
      renderRequests: [{
        input: { text: 'Hello' },
        voice: { languageCode: 'en-US', name: 'Kore' },
        audioConfig: { audioEncoding: 'MP3' }
      }]
    };
    const result = await service.createAudio(renderPlan);

    expect(audiobookApiServiceSpy.createAudio).toHaveBeenCalledWith(renderPlan, {}, undefined);
    expect(result).toBe(download);
  });

  it('delegates createAudioForRenderRequest to AudiobookApiService', async () => {
    const download = { blob: new Blob(['mp3']), filename: 'tts-render-request-1.mp3' };
    audiobookApiServiceSpy.createAudioForRenderRequest.and.resolveTo(download);

    const renderRequest = { input: { text: 'Only this request' }, voice: {}, audioConfig: {} };
    const result = await service.createAudioForRenderRequest(renderRequest);

    expect(audiobookApiServiceSpy.createAudioForRenderRequest).toHaveBeenCalledWith(renderRequest, {}, undefined);
    expect(result).toBe(download);
  });
});
