import { TestBed } from '@angular/core/testing';
import { AudiobookApiService } from './audiobook-api.service';
import { AudiobookWorkflowService } from './audiobook-workflow.service';
import { loadTestContractJson } from '../../../shared/test-contracts';

describe('AudiobookWorkflowService', () => {
  let service: AudiobookWorkflowService;
  let audiobookApiServiceSpy: jasmine.SpyObj<AudiobookApiService>;

  beforeEach(() => {
    audiobookApiServiceSpy = jasmine.createSpyObj<AudiobookApiService>('AudiobookApiService', [
      'post', 'createAudio', 'createAudioForRenderRequest'
    ]);

    TestBed.configureTestingModule({
      providers: [
        AudiobookWorkflowService,
        { provide: AudiobookApiService, useValue: audiobookApiServiceSpy }
      ]
    });
    service = TestBed.inject(AudiobookWorkflowService);
  });

  it('posts raw dialogue to the speaker voice analysis endpoint', async () => {
    const baseResponse = await loadTestContractJson<{ speakers: Array<{ speakerName: string; roleDescription: string; voiceSuggestion: string }>; projectId: string }>(
      'tts-workbench/speaker-voice-analysis/default/response.json'
    );
    audiobookApiServiceSpy.post.and.resolveTo({
      ...baseResponse,
      speakers: [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm voice' }],
      projectId: 'project-1'
    });

    const response = await service.analyzeSpeakers('Alice: Hello');

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/speaker-voice-analysis',
      { rawDialogue: 'Alice: Hello' },
      'Speaker voice analysis failed'
    );
    expect(response.speakers[0].speakerName).toBe('Alice');
    expect(response.projectId).toBe('project-1');
  });

  it('posts dialogue and speakers to the speaker split endpoint', async () => {
    const response = await loadTestContractJson<{ turns: Array<{ speaker: string; text: string }> }>(
      'tts-workbench/speaker-split-analysis/default/response.json'
    );
    audiobookApiServiceSpy.post.and.resolveTo({
      ...response,
      turns: [{ speaker: 'Alice', text: 'Hello' }]
    });

    const speakers = [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm voice' }];
    const turns = await service.splitDialogue('Alice: Hello', speakers, 'project-1');

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/speaker-split-analysis',
      { rawDialogue: 'Alice: Hello', speakers, projectId: 'project-1' },
      'Speaker split analysis failed'
    );
    expect(turns[0].text).toBe('Hello');
  });

  it('posts script preview turns to the save endpoint', async () => {
    audiobookApiServiceSpy.post.and.resolveTo({
      turns: [{ speaker: 'Alice', text: 'Hello there' }]
    });

    const turns = await service.saveScriptPreview('project-1', [{ speaker: 'Alice', text: 'Hello there' }]);

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/script-preview-save',
      { projectId: 'project-1', turns: [{ speaker: 'Alice', text: 'Hello there' }] },
      'Script preview save failed'
    );
    expect(turns[0].text).toBe('Hello there');
  });

  it('posts turns to the emotion annotation endpoint', async () => {
    const response = await loadTestContractJson<{ turns: Array<{ speaker: string; text: string }> }>(
      'tts-workbench/emotion-annotation-analysis/default/response.json'
    );
    audiobookApiServiceSpy.post.and.resolveTo({
      ...response,
      turns: [{ speaker: 'Alice', text: '[urgent] Hello!' }]
    });

    const turns = await service.annotateEmotions('project-1');

    expect(audiobookApiServiceSpy.post).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/emotion-annotation-analysis',
      { projectId: 'project-1' },
      'Emotion annotation analysis failed'
    );
    expect(turns[0].text).toBe('[urgent] Hello!');
  });

  it('posts final preview data to the final request endpoint', async () => {
    const response = await loadTestContractJson<{
      input: Record<string, unknown>;
      voice: Record<string, unknown>;
      audioConfig: Record<string, unknown>;
    }>('tts-workbench/final-request-preview/default/response.json');
    audiobookApiServiceSpy.post.and.resolveTo(response);

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
    const response = await loadTestContractJson<{ renderRequests: Array<{ input: Record<string, unknown>; voice: Record<string, unknown>; audioConfig: Record<string, unknown> }> }>(
      'tts-workbench/single-speaker-render-plan/default/response.json'
    );
    audiobookApiServiceSpy.post.and.resolveTo(response);

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
