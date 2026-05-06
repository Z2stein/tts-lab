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

  it('throws a user-facing error when analysis fails', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response('{}', { status: 500 }));

    await expectAsync(service.analyzeSpeakers('Alice: Hello')).toBeRejectedWithError('Speaker voice analysis failed (HTTP 500).');
  });
});
