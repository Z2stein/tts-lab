import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import {
  AnnotatedSpeakerTurn,
  FinalTtsRequestPreview,
  SingleSpeakerRenderPlan,
  SingleSpeakerRenderRequest,
  SpeakerSplitTurn,
  SpeakerVoiceAnalysisItem,
  TtsWorkbenchService
} from './tts-workbench.service';

@Component({
  selector: 'app-tts-workbench-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './tts-workbench-page.component.html',
  styleUrl: './tts-workbench-page.component.css'
})
export class TtsWorkbenchPageComponent {
  rawDialogueControl = new FormControl('', { nonNullable: true });
  promptControl = new FormControl('A conversation between the detected speakers.', { nonNullable: true });
  languageCodeControl = new FormControl('en-US', { nonNullable: true });
  modelNameControl = new FormControl('{{google-model}}', { nonNullable: true });
  audioEncodingControl = new FormControl('MP3', { nonNullable: true });

  speakers: SpeakerVoiceAnalysisItem[] = [];
  speakerTurns: SpeakerSplitTurn[] = [];
  annotatedTurns: AnnotatedSpeakerTurn[] = [];
  finalRequest: FinalTtsRequestPreview | null = null;
  singleSpeakerRenderPlan: SingleSpeakerRenderPlan | null = null;
  loadingAction: string | null = null;
  error: string | null = null;

  constructor(private readonly ttsWorkbenchService: TtsWorkbenchService) {}

  async analyzeSpeakers(): Promise<void> {
    await this.runStep('speakers', async () => {
      this.speakers = await this.ttsWorkbenchService.analyzeSpeakers(this.rawDialogueControl.value);
      this.speakerTurns = [];
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.singleSpeakerRenderPlan = null;
    }, 'Speaker voice analysis failed.');
  }

  async splitDialogue(): Promise<void> {
    await this.runStep('split', async () => {
      this.speakerTurns = await this.ttsWorkbenchService.splitDialogue(this.rawDialogueControl.value, this.speakers);
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.singleSpeakerRenderPlan = null;
    }, 'Speaker split analysis failed.');
  }

  async annotateEmotions(): Promise<void> {
    await this.runStep('emotions', async () => {
      this.annotatedTurns = await this.ttsWorkbenchService.annotateEmotions(this.speakerTurns);
      this.finalRequest = null;
      this.singleSpeakerRenderPlan = null;
    }, 'Emotion annotation analysis failed.');
  }

  async generateFinalJson(): Promise<void> {
    await this.runStep('final', async () => {
      this.finalRequest = await this.ttsWorkbenchService.generateFinalJson({
        prompt: this.promptControl.value,
        speakers: this.speakers,
        annotatedTurns: this.annotatedTurns,
        languageCode: this.languageCodeControl.value,
        modelName: this.modelNameControl.value,
        audioEncoding: this.audioEncodingControl.value
      });
      this.singleSpeakerRenderPlan = null;
    }, 'Final request preview failed.');
  }

  async planSingleSpeakerRenderRequests(): Promise<void> {
    if (!this.finalRequest) {
      return;
    }

    await this.runStep('render-plan', async () => {
      this.singleSpeakerRenderPlan = await this.ttsWorkbenchService.planSingleSpeakerRenderRequests(this.finalRequest!);
    }, 'Single-speaker render plan preview failed.');
  }

  async createAudio(): Promise<void> {
    if (!this.singleSpeakerRenderPlan || this.singleSpeakerRenderRequests.length === 0) {
      return;
    }

    await this.runStep('create-audio', async () => {
      const download = await this.ttsWorkbenchService.createAudio(this.singleSpeakerRenderPlan!);
      this.downloadBlob(download.blob, download.filename);
    }, 'Audio creation failed.');
  }

  updateSpeakerName(speaker: SpeakerVoiceAnalysisItem, event: Event): void {
    speaker.speakerName = this.eventValue(event);
  }

  updateSpeakerRoleDescription(speaker: SpeakerVoiceAnalysisItem, event: Event): void {
    speaker.roleDescription = this.eventValue(event);
  }

  updateSpeakerVoiceSuggestion(speaker: SpeakerVoiceAnalysisItem, event: Event): void {
    speaker.voiceSuggestion = this.eventValue(event);
  }

  updateSpeakerTurnSpeaker(turn: SpeakerSplitTurn, event: Event): void {
    turn.speaker = this.eventValue(event);
  }

  updateSpeakerTurnText(turn: SpeakerSplitTurn, event: Event): void {
    turn.text = this.eventValue(event);
  }

  updateAnnotatedTurnSpeaker(turn: AnnotatedSpeakerTurn, event: Event): void {
    turn.speaker = this.eventValue(event);
  }

  updateAnnotatedTurnText(turn: AnnotatedSpeakerTurn, event: Event): void {
    turn.text = this.eventValue(event);
  }

  get finalRequestJson(): string {
    return this.finalRequest ? JSON.stringify(this.finalRequest, null, 2) : '';
  }

  get singleSpeakerRenderRequests(): SingleSpeakerRenderRequest[] {
    return this.singleSpeakerRenderPlan?.renderRequests ?? [];
  }

  singleSpeakerRenderRequestJson(renderRequest: SingleSpeakerRenderRequest): string {
    return JSON.stringify(renderRequest, null, 2);
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }

  private eventValue(event: Event): string {
    return event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement
      ? event.target.value
      : '';
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  }

  private async runStep(action: string, step: () => Promise<void>, fallbackMessage: string): Promise<void> {
    this.loadingAction = action;
    this.error = null;

    try {
      await step();
    } catch (error) {
      this.error = error instanceof Error ? error.message : fallbackMessage;
    } finally {
      this.loadingAction = null;
    }
  }
}
