import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { SpeakerVoiceSuggestion, TtsWorkbenchService } from './tts-workbench.service';

@Component({
  selector: 'app-tts-workbench-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './tts-workbench-page.component.html',
  styleUrl: './tts-workbench-page.component.css'
})
export class TtsWorkbenchPageComponent {
  rawDialogueControl = new FormControl('', { nonNullable: true });
  speakers: SpeakerVoiceSuggestion[] = [];
  loading = false;
  error: string | null = null;
  submitted = false;

  constructor(private readonly ttsWorkbenchService: TtsWorkbenchService) {}

  async analyze(): Promise<void> {
    this.error = null;
    this.speakers = [];
    this.loading = true;
    this.submitted = true;

    try {
      this.speakers = await this.ttsWorkbenchService.analyzeSpeakerVoices(this.rawDialogueControl.value);
    } catch (e) {
      this.error = 'Speaker voice analysis failed.';
      console.error('[tts-workbench-page] Speaker voice analysis failed', e);
    } finally {
      this.loading = false;
    }
  }
}
