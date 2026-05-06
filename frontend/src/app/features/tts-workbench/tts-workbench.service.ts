import { Injectable } from '@angular/core';
import { CurrentUserService } from '../../current-user.service';

export type SpeakerVoiceSuggestion = {
  speakerName: string;
  roleDescription: string;
  voiceSuggestion: string;
};

type SpeakerVoiceAnalysisResponse = {
  speakers: SpeakerVoiceSuggestion[];
};

@Injectable({
  providedIn: 'root'
})
export class TtsWorkbenchService {
  constructor(private readonly currentUserService: CurrentUserService) {}

  async analyzeSpeakerVoices(rawDialogue: string): Promise<SpeakerVoiceSuggestion[]> {
    console.info('[tts-workbench] Sending speaker voice analysis request', { textLength: rawDialogue.length });

    let response: Response;
    try {
      response = await fetch('/api/projects/tts-workbench/speaker-voice-analysis', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
        },
        body: JSON.stringify({ rawDialogue })
      });
    } catch (error) {
      console.error('[tts-workbench] Network error while calling backend', error);
      throw new Error('Backend is unreachable. Please try again in a moment.');
    }

    if (!response.ok) {
      console.error('[tts-workbench] Backend returned non-OK status', { status: response.status });
      throw new Error(`Backend request failed (HTTP ${response.status}).`);
    }

    const data = (await response.json()) as SpeakerVoiceAnalysisResponse;
    console.info('[tts-workbench] Request succeeded', { speakers: data.speakers.length });
    return data.speakers;
  }
}
