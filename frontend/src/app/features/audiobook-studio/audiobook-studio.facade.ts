import { computed, Injectable, signal } from '@angular/core';
import {
  AudiobookWorkflowService,
  FinalTtsRequestPreview,
  SpeakerVoiceAnalysisItem,
} from '../audiobook-shared/service/audiobook-workflow.service';
import {
  SingleSpeakerRenderPlan,
} from '../audiobook-shared/service/audiobook-api.service';
import {
  AnnotatedSpeakerTurn,
  ScriptGroup,
  SpeakerSplitTurn,
} from './models/audiobook-studio.types';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from '../audiobook-shared/service/render-request-audio.service';

@Injectable()
export class AudiobookStudioFacade {
  private readonly _cast = signal<SpeakerVoiceAnalysisItem[]>([]);
  private readonly _scriptTurns = signal<SpeakerSplitTurn[]>([]);
  private readonly _annotatedTurns = signal<AnnotatedSpeakerTurn[]>([]);
  private readonly _finalRequest = signal<FinalTtsRequestPreview | null>(null);
  private readonly _audioProductionPlan = signal<SingleSpeakerRenderPlan | null>(null);
  private readonly _castReviewed = signal(false);
  private readonly _scriptApproved = signal(false);
  private readonly _performanceNotesStale = signal(false);
  private readonly _loadingAction = signal<string | null>(null);
  private readonly _error = signal<string | null>(null);
  private readonly _editingCastIndex = signal<number | null>(null);
  private readonly _castEditDraft = signal<SpeakerVoiceAnalysisItem | null>(null);
  private readonly _editingScriptTurnIndex = signal<number | null>(null);
  private readonly _scriptTurnEditDraft = signal<SpeakerSplitTurn | null>(null);
  private readonly _currentProjectId = signal<string | null>(null);

  readonly cast = this._cast.asReadonly();
  readonly scriptTurns = this._scriptTurns.asReadonly();
  readonly annotatedTurns = this._annotatedTurns.asReadonly();
  readonly finalRequest = this._finalRequest.asReadonly();
  readonly audioProductionPlan = this._audioProductionPlan.asReadonly();
  readonly castReviewed = this._castReviewed.asReadonly();
  readonly scriptApproved = this._scriptApproved.asReadonly();
  readonly performanceNotesStale = this._performanceNotesStale.asReadonly();
  readonly loadingAction = this._loadingAction.asReadonly();
  readonly error = this._error.asReadonly();
  readonly editingCastIndex = this._editingCastIndex.asReadonly();
  readonly castEditDraft = this._castEditDraft.asReadonly();
  readonly editingScriptTurnIndex = this._editingScriptTurnIndex.asReadonly();
  readonly scriptTurnEditDraft = this._scriptTurnEditDraft.asReadonly();
  readonly currentProjectId = this._currentProjectId.asReadonly();

  readonly scriptGroups = computed<ScriptGroup[]>(() =>
    this._scriptTurns().reduce<ScriptGroup[]>((groups, turn, index) => {
      const last = groups[groups.length - 1];
      if (last?.speaker === turn.speaker) {
        last.turns.push({ index, turn });
      } else {
        groups.push({ speaker: turn.speaker, turns: [{ index, turn }] });
      }
      return groups;
    }, [])
  );

  readonly speakerOptions = computed<string[]>(() => {
    const cast = this._cast();
    const scriptTurns = this._scriptTurns();
    const draft = this._scriptTurnEditDraft();
    const speakers = new Set(cast.map((s) => s.speakerName).filter(Boolean));
    const hasNarrator =
      scriptTurns.some((t) => t.speaker.toLowerCase() === 'narrator') ||
      cast.some((s) => s.speakerName.toLowerCase() === 'narrator');
    if (hasNarrator) speakers.add('Narrator');
    if (draft?.speaker) speakers.add(draft.speaker);
    return Array.from(speakers);
  });

  /** Called after audio states are reset; the page uses this to destroy WaveSurfer instances. */
  onAudioReset: (() => void) | null = null;

  constructor(
    private readonly audiobookWorkflowService: AudiobookWorkflowService,
    private readonly renderRequestAudioService: RenderRequestAudioService,
    private readonly fullAudioGenerationService: FullAudioGenerationService,
  ) {}

  // ── Direct setters (spec writes component.cast = [...] etc.) ──────────────

  setCast(value: SpeakerVoiceAnalysisItem[]): void { this._cast.set(value); }
  setScriptTurns(value: SpeakerSplitTurn[]): void { this._scriptTurns.set(value); }
  setAnnotatedTurns(value: AnnotatedSpeakerTurn[]): void { this._annotatedTurns.set(value); }
  setFinalRequest(value: FinalTtsRequestPreview | null): void { this._finalRequest.set(value); }
  setAudioProductionPlan(value: SingleSpeakerRenderPlan | null): void { this._audioProductionPlan.set(value); }
  setCastReviewed(value: boolean): void { this._castReviewed.set(value); }
  setScriptApproved(value: boolean): void { this._scriptApproved.set(value); }
  setPerformanceNotesStale(value: boolean): void { this._performanceNotesStale.set(value); }

  // ── Pipeline operations ───────────────────────────────────────────────────

  async analyzeStory(storyText: string): Promise<void> {
    await this.runStep('cast', async () => {
      const analysis = await this.audiobookWorkflowService.analyzeSpeakers(storyText);
      this._cast.set(analysis.speakers);
      this._currentProjectId.set(analysis.projectId);
      this._scriptTurns.set([]);
      this._annotatedTurns.set([]);
      this._finalRequest.set(null);
      this._audioProductionPlan.set(null);
      this._castReviewed.set(false);
      this._scriptApproved.set(false);
      this._performanceNotesStale.set(false);
      this.cancelCastEdit();
      this.cancelScriptTurnEdit();
      this.resetAudio();
    }, 'Story analysis failed.');
  }

  async createScriptPreview(storyText: string): Promise<void> {
    await this.runStep('script', async () => {
      const projectId = this._currentProjectId();
      if (!projectId) {
        throw new Error('Story analysis did not return a project id.');
      }
      const scriptTurns = await this.audiobookWorkflowService.splitDialogue(storyText, this._cast(), projectId);
      this._scriptTurns.set(scriptTurns);
      this._annotatedTurns.set([]);
      this._finalRequest.set(null);
      this._audioProductionPlan.set(null);
      this._castReviewed.set(true);
      this._scriptApproved.set(false);
      this._performanceNotesStale.set(false);
      this.cancelCastEdit();
      this.cancelScriptTurnEdit();
      this.resetAudio();
    }, 'Script preview failed.');
  }

  async createPerformanceNotes(): Promise<void> {
    await this.runStep('notes', async () => {
      if (this._editingScriptTurnIndex() !== null) {
        throw new Error('Save or cancel the script edit before adding emotion and pacing.');
      }
      const projectId = this._currentProjectId();
      if (!projectId) {
        throw new Error('Story analysis did not return a project id.');
      }
      const annotatedTurns = await this.audiobookWorkflowService.annotateEmotions(projectId);
      this._annotatedTurns.set(annotatedTurns);
      this._finalRequest.set(null);
      this._audioProductionPlan.set(null);
      this._performanceNotesStale.set(false);
      this.resetAudio();
    }, 'Performance notes failed.');
  }

  async createAudioProductionPlan(options: {
    prompt: string;
    languageCode: string;
    modelName: string;
    audioEncoding: string;
  }): Promise<void> {
    await this.runStep('plan', async () => {
      const finalRequest = await this.audiobookWorkflowService.generateFinalJson({
        prompt: options.prompt,
        speakers: this._cast(),
        annotatedTurns: this._annotatedTurns(),
        languageCode: options.languageCode,
        modelName: options.modelName,
        audioEncoding: options.audioEncoding,
      });
      this._finalRequest.set(finalRequest);
      const audioProductionPlan = await this.audiobookWorkflowService.planSingleSpeakerRenderRequests(finalRequest);
      this._audioProductionPlan.set(audioProductionPlan);
      this.resetAudio();
    }, 'Audio production plan failed.');
  }

  approveScript(): void {
    this._scriptApproved.set(true);
    this._performanceNotesStale.set(true);
  }

  // ── Edit operations ───────────────────────────────────────────────────────

  startCastEdit(index: number): void {
    this._editingCastIndex.set(index);
    this._castEditDraft.set({ ...this._cast()[index] });
  }

  saveCastEdit(index: number): void {
    const draft = this._castEditDraft();
    if (!draft) return;
    this._cast.update((cast) => cast.map((s, i) => (i === index ? { ...draft } : s)));
    this.cancelCastEdit();
  }

  cancelCastEdit(): void {
    this._editingCastIndex.set(null);
    this._castEditDraft.set(null);
  }

  startScriptTurnEdit(index: number): void {
    this._editingScriptTurnIndex.set(index);
    this._scriptTurnEditDraft.set({ ...this._scriptTurns()[index] });
  }

  async saveScriptTurnEdit(index: number): Promise<void> {
    await this.runStep('script-edit', async () => {
      const draft = this._scriptTurnEditDraft();
      const projectId = this._currentProjectId();
      if (!draft) return;
      if (!projectId) {
        throw new Error('Story analysis did not return a project id.');
      }

      const nextTurns = this._scriptTurns().map((turn, i) => (i === index ? { ...draft } : { ...turn }));
      const persistedTurns = await this.audiobookWorkflowService.saveScriptPreview(projectId, nextTurns);
      this._scriptTurns.set(persistedTurns);
      this.cancelScriptTurnEdit();
      this._scriptApproved.set(false);
      if (this._annotatedTurns().length > 0) {
        this._performanceNotesStale.set(true);
        this._finalRequest.set(null);
        this._audioProductionPlan.set(null);
        this.resetAudio();
      }
    }, 'Script turn save failed.');
  }

  cancelScriptTurnEdit(): void {
    this._editingScriptTurnIndex.set(null);
    this._scriptTurnEditDraft.set(null);
  }

  // ── Reset ─────────────────────────────────────────────────────────────────

  resetPipeline(): void {
    this._cast.set([]);
    this._scriptTurns.set([]);
    this._annotatedTurns.set([]);
    this._finalRequest.set(null);
    this._audioProductionPlan.set(null);
    this._castReviewed.set(false);
    this._scriptApproved.set(false);
    this._performanceNotesStale.set(false);
    this._error.set(null);
    this._currentProjectId.set(null);
    this.cancelCastEdit();
    this.cancelScriptTurnEdit();
    this.resetAudio();
  }

  setCurrentProjectId(id: string): void {
    this._currentProjectId.set(id);
  }

  private resetAudio(): void {
    this.renderRequestAudioService.abortAll();
    this.renderRequestAudioService.revokeUrls();
    this.fullAudioGenerationService.clearAudio();
    this.onAudioReset?.();
  }

  private async runStep(action: string, step: () => Promise<void>, fallbackMessage: string): Promise<void> {
    this._loadingAction.set(action);
    this._error.set(null);
    try {
      await step();
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : fallbackMessage);
    } finally {
      this._loadingAction.set(null);
    }
  }
}
