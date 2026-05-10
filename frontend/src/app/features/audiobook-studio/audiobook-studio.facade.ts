import { computed, Injectable, signal } from '@angular/core';
import {
  FinalTtsRequestPreview,
  SingleSpeakerRenderPlan,
  SpeakerVoiceAnalysisItem,
  TtsWorkbenchService,
} from '../tts-workbench/tts-workbench.service';
import { WorkflowService, WorkflowSessionResponse } from './services/workflow.service';
import {
  AnnotatedSpeakerTurn,
  ScriptGroup,
  SpeakerSplitTurn,
} from './models/audiobook-studio.types';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from './services/render-request-audio.service';

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
  private readonly _sessionId = signal<string | null>(null);

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
  readonly sessionId = this._sessionId.asReadonly();

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
    private readonly workflowService: WorkflowService,
    private readonly ttsWorkbenchService: TtsWorkbenchService,
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
      // Step 1: Analyze speakers using TTS workbench service
      const cast = await this.ttsWorkbenchService.analyzeSpeakers(storyText);

      // Step 2: Create workflow session and discover speakers on backend for persistence
      try {
        const createResponse = await this.workflowService.createWorkflow(storyText).toPromise();
        if (createResponse?.id) {
          this._sessionId.set(createResponse.id);
          // Optionally trigger speaker discovery on backend for audit trail
          // but don't block on it since we already have the speakers
          this.workflowService.discoverSpeakers(createResponse.id).toPromise().catch(() => {
            // Ignore errors from background workflow call
          });
        }
      } catch (err) {
        // Workflow session creation failed, but we can continue with local processing
        console.warn('Failed to create workflow session, continuing without persistence', err);
      }

      this._cast.set(cast);
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
      // Generate script using TTS workbench service
      const scriptTurns = await this.ttsWorkbenchService.splitDialogue(storyText, this._cast());

      // Optionally persist to workflow session
      const sessionId = this._sessionId();
      if (sessionId) {
        // Cast TTS workbench speakers to workflow service format for persistence
        const workflowSpeakers = this._cast().map(s => ({
          speakerName: s.speakerName,
          roleDescription: s.roleDescription,
          voiceSuggestion: { getKey: () => s.voiceSuggestion }
        }));
        this.workflowService.splitDialogue(sessionId, workflowSpeakers).toPromise().catch(() => {
          // Ignore errors from background workflow call
        });
      }

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
      // Annotate emotions using TTS workbench service
      const annotatedTurns = await this.ttsWorkbenchService.annotateEmotions(this._scriptTurns());

      // Optionally persist to workflow session
      const sessionId = this._sessionId();
      if (sessionId) {
        this.workflowService.annotateDialogue(sessionId, this._scriptTurns()).toPromise().catch(() => {
          // Ignore errors from background workflow call
        });
      }

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
      // Generate final request using TTS workbench service
      const finalRequest = await this.ttsWorkbenchService.generateFinalJson({
        prompt: options.prompt,
        speakers: this._cast(),
        annotatedTurns: this._annotatedTurns(),
        languageCode: options.languageCode,
        modelName: options.modelName,
        audioEncoding: options.audioEncoding,
      });
      this._finalRequest.set(finalRequest);

      // Generate audio production plan
      const audioProductionPlan = await this.ttsWorkbenchService.planSingleSpeakerRenderRequests(finalRequest);
      this._audioProductionPlan.set(audioProductionPlan);

      // Optionally persist to workflow session
      const sessionId = this._sessionId();
      if (sessionId) {
        // Build voice assignments map from cast (TtsWorkbench voiceSuggestion is a string)
        const voiceAssignments = new Map(
          this._cast().map(speaker => [speaker.speakerName, speaker.voiceSuggestion])
        );

        this.workflowService.configureOutput(
          sessionId,
          options.languageCode,
          options.modelName,
          options.audioEncoding,
          voiceAssignments
        ).toPromise().catch(() => {
          // Ignore errors from background workflow call
        });
      }

      this.resetAudio();
    }, 'Audio production plan failed.');
  }

  approveScript(): void {
    this._scriptApproved.set(true);
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

  saveScriptTurnEdit(index: number): void {
    const draft = this._scriptTurnEditDraft();
    if (!draft) return;
    this._scriptTurns.update((turns) => turns.map((t, i) => (i === index ? { ...draft } : t)));
    this.cancelScriptTurnEdit();
    this._scriptApproved.set(false);
    if (this._annotatedTurns().length > 0) {
      this._performanceNotesStale.set(true);
      this._finalRequest.set(null);
      this._audioProductionPlan.set(null);
      this.resetAudio();
    }
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
    this._sessionId.set(null);
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
