import { TtsWorkbenchService } from './tts-workbench.service';

describe('TtsWorkbenchService', () => {
  it('posts raw dialogue to the speaker voice analysis endpoint', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({
      speakers: [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm voice' }]
    }), { status: 200 }));

    const speakers = await service.analyzeSpeakers('Alice: Hello');

    expect(window.fetch).toHaveBeenCalledWith('/api/projects/tts-workbench/speaker-voice-analysis', jasmine.objectContaining({ method: 'POST' }));
    expect(speakers[0].speakerName).toBe('Alice');
  });

  it('posts dialogue and speakers to the speaker split endpoint', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({
      turns: [{ speaker: 'Alice', text: 'Hello' }]
    }), { status: 200 }));

    const turns = await service.splitDialogue('Alice: Hello', [
      { speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm voice' }
    ]);

    expect(window.fetch).toHaveBeenCalledWith('/api/projects/tts-workbench/speaker-split-analysis', jasmine.objectContaining({ method: 'POST' }));
    expect(turns[0].text).toBe('Hello');
  });

  it('posts turns to the emotion annotation endpoint', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({
      turns: [{ speaker: 'Alice', text: '[urgent] Hello!' }]
    }), { status: 200 }));

    const turns = await service.annotateEmotions([{ speaker: 'Alice', text: 'Hello!' }]);

    expect(window.fetch).toHaveBeenCalledWith('/api/projects/tts-workbench/emotion-annotation-analysis', jasmine.objectContaining({ method: 'POST' }));
    expect(turns[0].text).toBe('[urgent] Hello!');
  });

  it('posts final preview data to the final request endpoint', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({
      input: { prompt: 'Prompt' },
      voice: { languageCode: 'en-US' },
      audioConfig: { audioEncoding: 'MP3' }
    }), { status: 200 }));

    const finalJson = await service.generateFinalJson({
      prompt: 'Prompt',
      speakers: [],
      annotatedTurns: [],
      languageCode: 'en-US',
      modelName: '{{google-model}}',
      audioEncoding: 'MP3'
    });

    expect(window.fetch).toHaveBeenCalledWith('/api/projects/tts-workbench/final-request-preview', jasmine.objectContaining({ method: 'POST' }));
    expect((finalJson.audioConfig as any).audioEncoding).toBe('MP3');
  });

  it('throws a user-facing error when analysis fails', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({
      status: 502,
      code: 'TTS_WORKBENCH_PROVIDER_FAILED',
      message: 'The speaker voice analysis provider is currently unavailable. Please try again later.',
      requestId: 'request-1'
    }), { status: 502 }));

    await expectAsync(service.analyzeSpeakers('Alice: Hello'))
      .toBeRejectedWithError('The speaker voice analysis provider is currently unavailable. Please try again later.');
  });

  it('falls back to HTTP status when backend error body is unavailable', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response('not-json', { status: 500 }));

    await expectAsync(service.analyzeSpeakers('Alice: Hello')).toBeRejectedWithError('Speaker voice analysis failed (HTTP 500).');
  });

});
