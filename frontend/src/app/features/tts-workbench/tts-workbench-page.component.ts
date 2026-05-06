import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { SpeakerVoiceAnalysisItem, TtsWorkbenchService } from './tts-workbench.service';

@Component({
  selector: 'app-tts-workbench-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './tts-workbench-page.component.html',
  styleUrl: './tts-workbench-page.component.css'
})
export class TtsWorkbenchPageComponent {
  rawDialogueControl = new FormControl('', { nonNullable: true });
  speakers: SpeakerVoiceAnalysisItem[] = [];
  loading = false;
  error: string | null = null;

  constructor(private readonly ttsWorkbenchService: TtsWorkbenchService) {}

  async analyzeSpeakers(): Promise<void> {
    this.loading = true;
    this.error = null;
    this.speakers = [];

    try {
      this.speakers = await this.ttsWorkbenchService.analyzeSpeakers(this.rawDialogueControl.value);
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Speaker voice analysis failed.';
    } finally {
      this.loading = false;
    }
  }
}
