import { Injectable } from '@angular/core';
import { CurrentUserService } from '../../current-user.service';

export interface SpeakerVoiceAnalysisItem {
  speakerName: string;
  roleDescription: string;
  voiceSuggestion: string;
}

interface SpeakerVoiceAnalysisResponse {
  speakers: SpeakerVoiceAnalysisItem[];
}

@Injectable({ providedIn: 'root' })
export class TtsWorkbenchService {
  constructor(private readonly currentUserService: CurrentUserService) {}

  async analyzeSpeakers(rawDialogue: string): Promise<SpeakerVoiceAnalysisItem[]> {
    const response = await fetch('/api/projects/tts-workbench/speaker-voice-analysis', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': await this.currentUserService.ensureCsrfToken()
      },
      body: JSON.stringify({ rawDialogue })
    });

    if (!response.ok) {
      throw new Error(`Speaker voice analysis failed (HTTP ${response.status}).`);
    }

    const data = (await response.json()) as SpeakerVoiceAnalysisResponse;
    return data.speakers;
  }
}
