import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  AnnotatedSpeakerTurn,
  FinalTtsRequestPreview,
  SingleSpeakerRenderPlan,
  SingleSpeakerRenderRequest,
  SpeakerSplitTurn,
  SpeakerVoiceAnalysisItem,
  TtsWorkbenchService
} from '../tts-workbench/tts-workbench.service';

interface ScriptGroup {
  speaker: string;
  turns: SpeakerSplitTurn[];
}

interface AnnotatedMarkup {
  tags: string[];
  text: string;
}

@Component({
  selector: 'app-audiobook-studio-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './audiobook-studio-page.component.html',
  styleUrl: './audiobook-studio-page.component.css'
})
export class AudiobookStudioPageComponent {
  readonly sampleStory = `Narrator: The last train had already left when Mara found the brass key under the station clock.
Mara: Jonas, tell me you did not hide this here all winter.
Jonas: I was protecting it. The map said the keeper would know when the hour came.
Station Keeper: The hour came ten minutes ago, and the tunnels are listening.
Narrator: A warm light moved beneath the platform boards, slow as a waking ember.
Mara: Then we go now.
Jonas: Together?
Station Keeper: Together, and quietly. Stories travel faster underground.`;

  readonly languageCodeOptions = [
    { label: 'English (US) - en-US', value: 'en-US' },
    { label: 'German (Germany) - de-DE', value: 'de-DE' },
    { label: 'French (France) - fr-FR', value: 'fr-FR' },
    { label: 'Spanish (Spain) - es-ES', value: 'es-ES' },
    { label: 'Japanese (Japan) - ja-JP', value: 'ja-JP' }
  ];

  readonly modelNameOptions = [
    'gemini-3.1-flash-tts-preview',
    'gemini-2.5-pro-tts',
    'gemini-2.5-flash-tts'
  ];

  storyTextControl = new FormControl('', { nonNullable: true });
  promptControl = new FormControl('An immersive audiobook performance with a clear narrator and distinct character voices.', { nonNullable: true });
  languageCodeControl = new FormControl('en-US', { nonNullable: true });
  modelNameControl = new FormControl('gemini-3.1-flash-tts-preview', { nonNullable: true });
  audioEncodingControl = new FormControl('MP3', { nonNullable: true });

  cast: SpeakerVoiceAnalysisItem[] = [];
  scriptTurns: SpeakerSplitTurn[] = [];
  annotatedTurns: AnnotatedSpeakerTurn[] = [];
  finalRequest: FinalTtsRequestPreview | null = null;
  audioProductionPlan: SingleSpeakerRenderPlan | null = null;
  loadingAction: string | null = null;
  error: string | null = null;
  fullPlanAudioLoading = false;
  fullPlanAudioError: string | null = null;
  renderRequestAudioStates: Record<number, { loading: boolean; error: string | null }> = {};

  constructor(private readonly ttsWorkbenchService: TtsWorkbenchService) {}

  get wordCount(): number {
    return this.storyTextControl.value.trim().split(/\s+/).filter(Boolean).length;
  }

  get characterCount(): number {
    return this.storyTextControl.value.length;
  }

  get scriptGroups(): ScriptGroup[] {
    return this.scriptTurns.reduce<ScriptGroup[]>((groups, turn) => {
      const lastGroup = groups[groups.length - 1];
      if (lastGroup?.speaker === turn.speaker) {
        lastGroup.turns.push(turn);
      } else {
        groups.push({ speaker: turn.speaker, turns: [turn] });
      }
      return groups;
    }, []);
  }

  get renderRequests(): SingleSpeakerRenderRequest[] {
    return this.audioProductionPlan?.renderRequests ?? [];
  }

  get finalRequestJson(): string {
    return this.finalRequest ? JSON.stringify(this.finalRequest, null, 2) : '';
  }

  get audioProductionPlanJson(): string {
    return this.audioProductionPlan ? JSON.stringify(this.audioProductionPlan, null, 2) : '';
  }

  useSampleStory(): void {
    this.storyTextControl.setValue(this.sampleStory);
    this.resetPipeline();
    this.error = null;
  }

  async analyzeStory(): Promise<void> {
    await this.runStep('cast', async () => {
      this.cast = await this.ttsWorkbenchService.analyzeSpeakers(this.storyTextControl.value);
      this.scriptTurns = [];
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.resetAudioStates();
    }, 'Story analysis failed.');
  }

  async createScriptPreview(): Promise<void> {
    await this.runStep('script', async () => {
      this.scriptTurns = await this.ttsWorkbenchService.splitDialogue(this.storyTextControl.value, this.cast);
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.resetAudioStates();
    }, 'Script preview failed.');
  }

  async createPerformanceNotes(): Promise<void> {
    await this.runStep('notes', async () => {
      this.annotatedTurns = await this.ttsWorkbenchService.annotateEmotions(this.scriptTurns);
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.resetAudioStates();
    }, 'Performance notes failed.');
  }

  async createAudioProductionPlan(): Promise<void> {
    await this.runStep('plan', async () => {
      this.finalRequest = await this.ttsWorkbenchService.generateFinalJson({
        prompt: this.promptControl.value,
        speakers: this.cast,
        annotatedTurns: this.annotatedTurns,
        languageCode: this.languageCodeControl.value,
        modelName: this.modelNameControl.value,
        audioEncoding: this.audioEncodingControl.value
      });
      this.audioProductionPlan = await this.ttsWorkbenchService.planSingleSpeakerRenderRequests(this.finalRequest);
      this.resetAudioStates();
    }, 'Audio production plan failed.');
  }

  async generateAudio(): Promise<void> {
    if (!this.audioProductionPlan || this.renderRequests.length === 0) {
      return;
    }

    this.fullPlanAudioLoading = true;
    this.fullPlanAudioError = null;

    try {
      const download = await this.ttsWorkbenchService.createAudio(this.audioProductionPlan);
      this.downloadBlob(download.blob, download.filename);
    } catch (error) {
      this.fullPlanAudioError = error instanceof Error ? error.message : 'Generate audio failed.';
    } finally {
      this.fullPlanAudioLoading = false;
    }
  }

  async generateAudioForRenderRequest(renderRequest: SingleSpeakerRenderRequest, requestIndex: number): Promise<void> {
    const state = this.renderRequestAudioState(requestIndex);
    state.loading = true;
    state.error = null;

    try {
      const download = await this.ttsWorkbenchService.createAudioForRenderRequest(renderRequest);
      this.downloadBlob(download.blob, `tts-render-request-${requestIndex + 1}.mp3`);
    } catch (error) {
      state.error = error instanceof Error ? error.message : 'Generate audio failed.';
    } finally {
      state.loading = false;
    }
  }

  markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
    const tags: string[] = [];
    let remainingText = turn.text.trimStart();
    let match = /^\[([^\]]+)]\s*/.exec(remainingText);

    while (match) {
      tags.push(match[1]);
      remainingText = remainingText.slice(match[0].length).trimStart();
      match = /^\[([^\]]+)]\s*/.exec(remainingText);
    }

    return { tags, text: remainingText };
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

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }

  private resetPipeline(): void {
    this.cast = [];
    this.scriptTurns = [];
    this.annotatedTurns = [];
    this.finalRequest = null;
    this.audioProductionPlan = null;
    this.resetAudioStates();
  }

  private resetAudioStates(): void {
    this.fullPlanAudioLoading = false;
    this.fullPlanAudioError = null;
    this.renderRequestAudioStates = {};
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
