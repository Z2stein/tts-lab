import { TtsWorkbenchService } from './tts-workbench.service';

describe('TtsWorkbenchService', () => {
  it('sends POST request to the speaker voice analysis endpoint and handles success', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({
      speakers: [
        { speakerName: 'Alice', roleDescription: 'Narrator', voiceSuggestion: 'Warm voice' }
      ]
    }), { status: 200 }));

    const speakers = await service.analyzeSpeakerVoices('Alice: Hello');

    expect(window.fetch).toHaveBeenCalledWith(
      '/api/projects/tts-workbench/speaker-voice-analysis',
      jasmine.objectContaining({ method: 'POST' })
    );
    expect(speakers[0].speakerName).toBe('Alice');
  });

  it('handles backend error', async () => {
    const service = new TtsWorkbenchService({ ensureCsrfToken: async () => 'csrf-token' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response('{}', { status: 500 }));

    await expectAsync(service.analyzeSpeakerVoices('Alice: Hello')).toBeRejected();
  });
});
