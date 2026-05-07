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
  readonly languageCodeOptions = [
    { label: 'Arabic (Egypt) - ar-EG', value: 'ar-EG' },
    { label: 'Dutch (Netherlands) - nl-NL', value: 'nl-NL' },
    { label: 'English (India) - en-IN', value: 'en-IN' },
    { label: 'English (US) - en-US', value: 'en-US' },
    { label: 'French (France) - fr-FR', value: 'fr-FR' },
    { label: 'German (Germany) - de-DE', value: 'de-DE' },
    { label: 'Hindi (India) - hi-IN', value: 'hi-IN' },
    { label: 'Indonesian (Indonesia) - id-ID', value: 'id-ID' },
    { label: 'Italian (Italy) - it-IT', value: 'it-IT' },
    { label: 'Japanese (Japan) - ja-JP', value: 'ja-JP' },
    { label: 'Korean (South Korea) - ko-KR', value: 'ko-KR' },
    { label: 'Marathi (India) - mr-IN', value: 'mr-IN' },
    { label: 'Polish (Poland) - pl-PL', value: 'pl-PL' },
    { label: 'Portuguese (Brazil) - pt-BR', value: 'pt-BR' },
    { label: 'Romanian (Romania) - ro-RO', value: 'ro-RO' },
    { label: 'Russian (Russia) - ru-RU', value: 'ru-RU' },
    { label: 'Spanish (Spain) - es-ES', value: 'es-ES' },
    { label: 'Tamil (India) - ta-IN', value: 'ta-IN' },
    { label: 'Telugu (India) - te-IN', value: 'te-IN' },
    { label: 'Thai (Thailand) - th-TH', value: 'th-TH' },
    { label: 'Turkish (Turkey) - tr-TR', value: 'tr-TR' },
    { label: 'Ukrainian (Ukraine) - uk-UA', value: 'uk-UA' },
    { label: 'Vietnamese (Vietnam) - vi-VN', value: 'vi-VN' }
  ];

  readonly modelNameOptions = [
    'gemini-3.1-flash-tts-preview',
    'gemini-2.5-pro-tts',
    'gemini-2.5-flash-tts'
  ];

  rawDialogueControl = new FormControl('', { nonNullable: true });
  promptControl = new FormControl('A conversation between the detected speakers.', { nonNullable: true });
  languageCodeControl = new FormControl('en-US', { nonNullable: true });
  modelNameControl = new FormControl('gemini-3.1-flash-tts-preview', { nonNullable: true });
  audioEncodingControl = new FormControl('MP3', { nonNullable: true });

  speakers: SpeakerVoiceAnalysisItem[] = [];
  speakerTurns: SpeakerSplitTurn[] = [];
  annotatedTurns: AnnotatedSpeakerTurn[] = [];
  finalRequest: FinalTtsRequestPreview | null = null;
  singleSpeakerRenderPlan: SingleSpeakerRenderPlan | null = null;
  loadingAction: string | null = null;
  error: string | null = null;
  fullPlanAudioLoading = false;
  fullPlanAudioError: string | null = null;
  renderRequestAudioStates: Record<number, { loading: boolean; error: string | null }> = {};

  constructor(private readonly ttsWorkbenchService: TtsWorkbenchService) {}

  async analyzeSpeakers(): Promise<void> {
    await this.runStep('speakers', async () => {
      this.speakers = await this.ttsWorkbenchService.analyzeSpeakers(this.rawDialogueControl.value);
      this.speakerTurns = [];
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.singleSpeakerRenderPlan = null;
      this.resetAudioStates();
    }, 'Speaker voice analysis failed.');
  }

  async splitDialogue(): Promise<void> {
    await this.runStep('split', async () => {
      this.speakerTurns = await this.ttsWorkbenchService.splitDialogue(this.rawDialogueControl.value, this.speakers);
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.singleSpeakerRenderPlan = null;
      this.resetAudioStates();
    }, 'Speaker split analysis failed.');
  }

  async annotateEmotions(): Promise<void> {
    await this.runStep('emotions', async () => {
      this.annotatedTurns = await this.ttsWorkbenchService.annotateEmotions(this.speakerTurns);
      this.finalRequest = null;
      this.singleSpeakerRenderPlan = null;
      this.resetAudioStates();
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
      this.resetAudioStates();
    }, 'Final request preview failed.');
  }

  async planSingleSpeakerRenderRequests(): Promise<void> {
    if (!this.finalRequest) {
      return;
    }

    await this.runStep('render-plan', async () => {
      this.singleSpeakerRenderPlan = await this.ttsWorkbenchService.planSingleSpeakerRenderRequests(this.finalRequest!);
      this.resetAudioStates();
    }, 'Single-speaker render plan preview failed.');
  }

  async createAudio(): Promise<void> {
    if (!this.singleSpeakerRenderPlan || this.singleSpeakerRenderRequests.length === 0) {
      return;
    }

    this.fullPlanAudioLoading = true;
    this.fullPlanAudioError = null;

    try {
      const download = await this.ttsWorkbenchService.createAudio(this.singleSpeakerRenderPlan);
      this.downloadBlob(download.blob, download.filename);
    } catch (error) {
      this.fullPlanAudioError = error instanceof Error ? error.message : 'Audio creation failed.';
    } finally {
      this.fullPlanAudioLoading = false;
    }
  }

  async createAudioForRenderRequest(renderRequest: SingleSpeakerRenderRequest, requestIndex: number): Promise<void> {
    const state = this.renderRequestAudioState(requestIndex);
    state.loading = true;
    state.error = null;

    try {
      const download = await this.ttsWorkbenchService.createAudioForRenderRequest(renderRequest);
      this.downloadBlob(download.blob, `tts-render-request-${requestIndex + 1}.mp3`);
    } catch (error) {
      state.error = error instanceof Error ? error.message : 'Audio creation failed.';
    } finally {
      state.loading = false;
    }
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

  renderRequestAudioState(requestIndex: number): { loading: boolean; error: string | null } {
    if (!this.renderRequestAudioStates[requestIndex]) {
      this.renderRequestAudioStates[requestIndex] = { loading: false, error: null };
    }
    return this.renderRequestAudioStates[requestIndex];
  }

  anyAudioLoading(): boolean {
    return this.fullPlanAudioLoading || Object.values(this.renderRequestAudioStates).some((state) => state.loading);
  }

  private resetAudioStates(): void {
    this.fullPlanAudioLoading = false;
    this.fullPlanAudioError = null;
    this.renderRequestAudioStates = {};
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
