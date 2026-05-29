import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, effect, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { AppErrorBannerService } from '../../shared/services/app-error-banner.service';
import { AudiobookWorkflowSnapshotResponse, SpeakerVoiceCatalogItem } from '../../shared/api-contract.generated';
import {
  AnnotatedSpeakerTurn,
  FinalTtsRequestPreviewResponse,
  SpeakerVoiceAnalysisItem,
  SingleSpeakerRenderPlanResponse,
  SingleSpeakerRenderRequest,
} from '../audiobook-shared/service/audiobook-workflow.service';
import { AiGenerationOverlayComponent } from '../../shared/components/ai-generation-overlay/ai-generation-overlay.component';
import { CastSectionComponent } from './components/cast-section/cast-section.component';
import { VoicePickerModalComponent } from './components/voice-picker/voice-picker-modal.component';
import { PerformanceNotesComponent } from './components/performance-notes/performance-notes.component';
import { ScriptReviewComponent } from './components/script-review/script-review.component';
import { ProjectTitleEditorComponent } from './components/project-title-editor/project-title-editor.component';
import { StoryInputComponent } from './components/story-input/story-input.component';
import { StudioHeroComponent } from './components/studio-hero/studio-hero.component';
import { CurrentTaskPanelComponent } from './components/current-task-panel/current-task-panel.component';
import { WaveformPlayerComponent } from './components/waveform-player/waveform-player.component';
import { WorkflowStepperComponent } from './components/workflow-stepper/workflow-stepper.component';
import { AutopilotModeToggleComponent, StudioMode } from './components/autopilot/autopilot-mode-toggle.component';
import { AutopilotSetupCardComponent } from './components/autopilot/autopilot-setup-card.component';
import { AutopilotProgressCardComponent } from './components/autopilot/autopilot-progress-card.component';
import { AutopilotService, AutopilotStepId } from './services/autopilot.service';
import {
  HERO_CAST,
  LANGUAGE_CODE_OPTIONS,
  MODEL_NAME_OPTIONS,
  SAMPLE_STORY,
  SPEAKER_ACCENTS
} from './data/studio-content';
import {
  AnnotatedMarkup,
  CurrentTask,
  HeroCastMember,
  IndexedSpeakerSplitTurn,
  RenderRequestAudioState,
  ScriptGroup,
  SpeakerAccent,
  SpeakerSplitTurn,
  WorkflowStep,
} from './models/audiobook-studio.types';
import { durationLabelFor, formatElapsedTime } from './utils/audio-format';
import { markupFor } from './utils/annotated-markup';
import { formatSpeakerDisplayName, normalizedSpeakerKey, speakerInitials } from './utils/speaker-name';
import { AudiobookStudioFacade } from './audiobook-studio.facade';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from '../audiobook-shared/service/render-request-audio.service';
import { ScrollService } from './services/scroll.service';
import { VoiceSampleService } from './services/voice-sample.service';
import { WaveSurferService } from './services/wave-surfer.service';
import { buildCurrentTask, buildWorkflowSteps, StudioWorkflowState } from './utils/studio-workflow-state';

// Re-export so the spec can import formatSpeakerDisplayName from this file path unchanged.
export { formatSpeakerDisplayName };

@Component({
  selector: 'app-audiobook-studio-workspace',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    StudioHeroComponent,
    WaveformPlayerComponent,
    WorkflowStepperComponent,
    ProjectTitleEditorComponent,
    StoryInputComponent,
    CastSectionComponent,
    ScriptReviewComponent,
    PerformanceNotesComponent,
    CurrentTaskPanelComponent,
    VoicePickerModalComponent,
    AiGenerationOverlayComponent,
    AutopilotModeToggleComponent,
    AutopilotSetupCardComponent,
    AutopilotProgressCardComponent,
  ],
  providers: [
    AudiobookStudioFacade,
    WaveSurferService,
    VoiceSampleService,
    RenderRequestAudioService,
    FullAudioGenerationService,
    ScrollService,
    AutopilotService,
  ],
  templateUrl: './audiobook-studio-page.component.html'
})
export class AudiobookStudioWorkspaceComponent implements AfterViewInit, OnChanges, OnDestroy {
  // ── Spec proxy: partGenerationTimeoutMs ───────────────────────────────────
  get partGenerationTimeoutMs(): number { return this.renderRequestAudioService.partGenerationTimeoutMs; }
  set partGenerationTimeoutMs(ms: number) { this.renderRequestAudioService.partGenerationTimeoutMs = ms; }

  readonly speakerStyleFn = (name: string | null | undefined) => this.speakerStyle(name);

  readonly heroCast: readonly HeroCastMember[] = HERO_CAST;
  readonly speakerAccents: readonly SpeakerAccent[] = SPEAKER_ACCENTS;
  readonly sampleStory = SAMPLE_STORY;
  readonly languageCodeOptions = LANGUAGE_CODE_OPTIONS;
  readonly modelNameOptions = MODEL_NAME_OPTIONS;

  storyTextControl = new FormControl('', { nonNullable: true });
  projectTitleControl = new FormControl('', { nonNullable: true });
  promptControl = new FormControl('An immersive audiobook performance with a clear narrator and distinct character voices.', { nonNullable: true });
  languageCodeControl = new FormControl('en-US', { nonNullable: true });
  modelNameControl = new FormControl('gemini-3.1-flash-tts-preview', { nonNullable: true });
  audioEncodingControl = new FormControl('MP3', { nonNullable: true });
  speakerAnalysisHintControl = new FormControl('', { nonNullable: true });
  speakerSplitHintControl = new FormControl('', { nonNullable: true });
  emotionAnnotationHintControl = new FormControl('', { nonNullable: true });
  projectTitleEditing = false;
  @Input() showHero = true;
  @Input() snapshot: AudiobookWorkflowSnapshotResponse | null = null;
  @Input() scrollToSectionAfterLoad: string | null = null;
  @Output() projectCreated = new EventEmitter<string>();
  // Emitted when Autopilot creates the project. The host updates the URL to the
  // audiobook link in place (no route reload) so the in-page run continues.
  @Output() autopilotProjectCreated = new EventEmitter<string>();

  private pendingScrollToSection: string | null = null;

  readonly studioMode = signal<StudioMode>('autopilot');
  // Resume page opens an in-progress project directly in the detailed Guided
  // view; new sessions (landing) keep the Autopilot default.
  @Input() set initialMode(mode: StudioMode) { this.studioMode.set(mode); }
  private autopilotActive = false;

  get autopilotSteps() { return this.autopilot.steps(); }
  get autopilotRunning(): boolean { return this.autopilot.running(); }
  get autopilotFinished(): boolean { return this.autopilot.finished(); }

  // Live progress for the "Generate audio preview" autopilot step: how many parts
  // are ready vs still open, which part is rendering, and any retry/merge state.
  get autopilotPreviewProgress(): string | null {
    const step = this.autopilotSteps.find(s => s.id === 'generate-preview');
    if (!step || step.status !== 'running') return null;

    const total = this.renderRequests.length;
    if (total === 0) return null;

    // Touch the per-second clock so this label refreshes while parts render.
    void this.renderRequestAudioService.clockTick;

    const ready = this.generatedPartCount();
    const open = this.partsToGenerateCount();
    const counts = open > 0
      ? `${ready} of ${total} parts ready, ${open} still open`
      : `${ready} of ${total} parts ready`;

    const serviceMessage = this.fullPlanAudioStatusMessage;
    if (serviceMessage && /retry/i.test(serviceMessage)) {
      return `${serviceMessage} — ${counts}`;
    }

    const currentIndex = this.currentGeneratingRequestIndex();
    if (currentIndex !== null) {
      return `Generating part ${currentIndex + 1} of ${total} — ${counts}`;
    }

    if (this.fullPlanAudioLoading && serviceMessage) {
      return `${serviceMessage} — ${counts}`;
    }

    return counts;
  }

  // ── Facade state proxies (spec reads/writes these directly) ───────────────

  get cast(): SpeakerVoiceAnalysisItem[] { return this.facade.cast(); }
  set cast(value: SpeakerVoiceAnalysisItem[]) { this.facade.setCast(value); }

  get scriptTurns(): SpeakerSplitTurn[] { return this.facade.scriptTurns(); }
  set scriptTurns(value: SpeakerSplitTurn[]) { this.facade.setScriptTurns(value); }

  get annotatedTurns(): AnnotatedSpeakerTurn[] { return this.facade.annotatedTurns(); }
  set annotatedTurns(value: AnnotatedSpeakerTurn[]) { this.facade.setAnnotatedTurns(value); }

  get finalRequest(): FinalTtsRequestPreviewResponse | null { return this.facade.finalRequest(); }
  set finalRequest(value: FinalTtsRequestPreviewResponse | null) { this.facade.setFinalRequest(value); }

  get audioProductionPlan(): SingleSpeakerRenderPlanResponse | null { return this.facade.audioProductionPlan(); }
  set audioProductionPlan(value: SingleSpeakerRenderPlanResponse | null) { this.facade.setAudioProductionPlan(value); }

  get audioAssets() { return this.facade.audioAssets(); }
  get productionSettings() { return this.facade.productionSettings(); }
  get sourceLanguageCode(): string { return this.facade.sourceLanguageCode(); }

  get castReviewed(): boolean { return this.facade.castReviewed(); }
  set castReviewed(value: boolean) { this.facade.setCastReviewed(value); }

  get scriptApproved(): boolean { return this.facade.scriptApproved(); }
  set scriptApproved(value: boolean) { this.facade.setScriptApproved(value); }

  get performanceNotesStale(): boolean { return this.facade.performanceNotesStale(); }
  set performanceNotesStale(value: boolean) { this.facade.setPerformanceNotesStale(value); }

  get performanceReady(): boolean { return this.facade.performanceReady(); }

  // Single source of truth: the section card mirrors the compact stepper so the
  // two can never disagree (and the step survives a reload — see workflowState).
  get performanceStepCompleted(): boolean {
    return this.workflowSteps.find(step => step.key === 'performance')?.status === 'completed';
  }

  get loadingAction(): string | null { return this.facade.loadingAction(); }
  get error(): string | null { return this.facade.error(); }

  get editingCastIndex(): number | null { return this.facade.editingCastIndex(); }
  get castEditDraft(): SpeakerVoiceAnalysisItem | null { return this.facade.castEditDraft(); }
  get addingSpeaker(): boolean { return this.facade.addingSpeaker(); }
  get addSpeakerDraft(): SpeakerVoiceAnalysisItem | null { return this.facade.addSpeakerDraft(); }
  get editingScriptTurnIndex(): number | null { return this.facade.editingScriptTurnIndex(); }
  get scriptTurnEditDraft(): SpeakerSplitTurn | null { return this.facade.scriptTurnEditDraft(); }

  // ── Full-audio delegated state ────────────────────────────────────────────
  get fullPlanAudioLoading(): boolean { return this.fullAudioGenerationService.loading; }
  get fullPlanAudioError(): string | null { return this.fullAudioGenerationService.error; }
  get fullPlanAudioStale(): boolean { return this.fullAudioGenerationService.stale; }
  get fullPlanAudioStatusMessage(): string | null { return this.fullAudioGenerationService.statusMessage; }
  get fullPlanAudioUrl(): string | null { return this.fullAudioGenerationService.audioUrl ?? this.facade.mergedAudioUrl(); }
  get fullPlanAudioFilename(): string | null { return this.fullAudioGenerationService.filename; }

  // ── Voice-sample delegated state ──────────────────────────────────────────
  get activeSampleKey(): string | null { return this.voiceSampleService.activeSampleKey; }

  fullPlanAudioPlaying = false;
  renderRequestAudioPlayingStates: Record<number, boolean> = {};
  voicePickerOpenForIndex: number | null = null;
  voicePickerForNewSpeaker = false;
  readonly audioSectionCollapsed = signal(false);
  private lastKnownProjectId: string | null = null;

  toggleAudioSection(): void {
    this.audioSectionCollapsed.update(v => !v);
  }

  constructor(
    private readonly facade: AudiobookStudioFacade,
    private readonly waveSurferService: WaveSurferService,
    private readonly voiceSampleService: VoiceSampleService,
    private readonly renderRequestAudioService: RenderRequestAudioService,
    private readonly fullAudioGenerationService: FullAudioGenerationService,
    private readonly scrollService: ScrollService,
    private readonly liveAnnouncer: LiveAnnouncer,
    private readonly errorBannerService: AppErrorBannerService,
    private readonly autopilot: AutopilotService,
  ) {
    effect(() => {
      const error = this.facade.error();
      if (error) {
        this.errorBannerService.showError(error);
      }
    });

    effect(() => {
      const requests = this.facade.audioProductionPlan()?.renderRequests ?? [];
      const cast = this.facade.cast();
      for (let i = 0; i < requests.length; i++) {
        const state = this.renderRequestAudioService.getState(i, requests[i], cast);
        if (state.error) {
          this.errorBannerService.showError(state.error);
          return;
        }
      }
    });
  }

  // ── Computed from facade signals ──────────────────────────────────────────

  get scriptGroups(): ScriptGroup[] { return this.facade.scriptGroups(); }
  get speakerOptions(): string[] { return this.facade.speakerOptions(); }

  get renderRequests(): SingleSpeakerRenderRequest[] { return this.facade.audioProductionPlan()?.renderRequests ?? []; }
  get finalRequestJson(): string { return this.facade.finalRequest() ? JSON.stringify(this.facade.finalRequest(), null, 2) : ''; }
  get audioProductionPlanJson(): string { return this.facade.audioProductionPlan() ? JSON.stringify(this.facade.audioProductionPlan(), null, 2) : ''; }
  get workflowSteps(): WorkflowStep[] { return buildWorkflowSteps(this.workflowState()); }
  get currentTask(): CurrentTask { return buildCurrentTask(this.workflowState()); }
  get castEditable(): boolean {
    const workflowStage = this.facade.workflowStage();
    return this.scriptTurns.length === 0 && (workflowStage === null || workflowStage === 'CAST_REVIEW' || workflowStage === 'CAST_APPROVED');
  }

  // ── User actions ──────────────────────────────────────────────────────────

  useSampleStory(): void {
    this.storyTextControl.setValue(this.sampleStory);
    this.facade.resetPipeline();
    this.projectTitleControl.setValue('');
    this.projectTitleEditing = false;
    this.lastKnownProjectId = null;
  }

  focusStoryInput(event?: Event): void {
    event?.preventDefault();
    this.studioMode.set('autopilot');
    setTimeout(() => {
      this.scrollService.scrollTo('autopilot-setup');
      this.scrollService.focusById('autopilot-story-text', { preventScroll: true });
    }, 50);
  }

  scrollToSection(sectionId: string, event?: Event): void {
    event?.preventDefault();
    this.scrollService.scrollTo(sectionId);
  }

  async analyzeStoryAndScroll(): Promise<void> {
    await this.analyzeStory();
    if (this.facade.cast().length > 0) {
      this.scrollService.scrollTo('cast-section');
    }
  }

  async approveCast(): Promise<void> {
    await this.facade.approveCast();
  }

  async createScriptPreviewAndScroll(): Promise<void> {
    await this.approveCast();
    await this.createScriptPreview();
    if (this.facade.scriptTurns().length > 0) {
      this.scrollService.scrollTo('script-section');
    }
  }

    async approveScriptAndContinueWorkflow(): Promise<void> {
    // Only approve when the script is not already approved. Re-approving an
    // already-approved project (e.g. reloaded at SCRIPT_APPROVED /
    // PERFORMANCE_READY) regresses the backend state and makes the subsequent
    // emotion annotation fail with "script must be approved".
    if (!this.scriptApproved) {
      await this.approveScript();
    }
    if (this.scriptApproved) {
      await this.createPerformanceNotes();
      this.scrollService.scrollTo('performance-section');
    }
  }

  async createPerformanceNotesAndScroll(): Promise<void> {
    await this.createPerformanceNotes();
    if (this.facade.annotatedTurns().length > 0) {
      this.scrollService.scrollTo('performance-section');
    }
  }

  async createAudioProductionPlanAndScroll(): Promise<void> {
    await this.createAudioProductionPlan();
    if (this.facade.audioProductionPlan()) {
      this.scrollService.scrollTo('audio-section');
    }
  }

  async analyzeStory(): Promise<void> {
    this.projectTitleControl.setValue('');
    this.projectTitleEditing = false;
    await this.facade.analyzeStory(this.storyTextControl.value, this.speakerAnalysisHintControl.value || undefined);
    this.projectTitleControl.setValue(this.facade.projectTitle());
    this.languageCodeControl.setValue(this.facade.productionSettings()?.languageCode ?? 'en-US');
    this.projectTitleEditing = false;
    this.lastKnownProjectId = this.facade.currentProjectId();
    const projectId = this.facade.currentProjectId();
    if (projectId) {
      if (this.autopilotActive) {
        // Update the URL to the audiobook link without a full route reload, so
        // the in-page Autopilot run keeps going (and a reload resumes the project).
        this.autopilotProjectCreated.emit(projectId);
      } else {
        this.projectCreated.emit(projectId);
      }
    }
    if (this.facade.cast().length > 0) {
      void this.liveAnnouncer.announce(`Found ${this.facade.cast().length} characters`, 'polite');
    } else if (this.facade.error()) {
      void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
    }
  }

  hydrateFromSnapshot(snapshot: AudiobookWorkflowSnapshotResponse): void {
    this.facade.hydrateFromSnapshot(snapshot);
    this.storyTextControl.setValue(snapshot.storyText ?? '');
    this.projectTitleControl.setValue(snapshot.title);
    this.promptControl.setValue(snapshot.productionSettings.prompt);
    this.languageCodeControl.setValue(snapshot.productionSettings.languageCode);
    this.modelNameControl.setValue(snapshot.productionSettings.modelName);
    this.audioEncodingControl.setValue(snapshot.productionSettings.audioEncoding);
    this.projectTitleEditing = false;
    this.lastKnownProjectId = snapshot.projectId;
  }

  async createScriptPreview(): Promise<void> {
    await this.facade.createScriptPreview(this.storyTextControl.value, this.speakerSplitHintControl.value || undefined);
    this.lastKnownProjectId = this.facade.currentProjectId();
    if (this.facade.scriptTurns().length > 0) {
      void this.liveAnnouncer.announce(`Script ready with ${this.facade.scriptTurns().length} turns`, 'polite');
    } else if (this.facade.error()) {
      void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
    }
  }

  async createPerformanceNotes(): Promise<void> {
    if (!this.facade.currentProjectId() && this.lastKnownProjectId) {
      this.facade.setCurrentProjectId(this.lastKnownProjectId);
    }
    await this.facade.createPerformanceNotes(this.emotionAnnotationHintControl.value || undefined);
    if (this.facade.annotatedTurns().length > 0) {
      void this.liveAnnouncer.announce('Performance notes added', 'polite');
    } else if (this.facade.error()) {
      void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
    }
  }

  async createAudioProductionPlan(): Promise<void> {
    await this.facade.createAudioProductionPlan({
      prompt: this.promptControl.value,
      languageCode: this.languageCodeControl.value,
      modelName: this.modelNameControl.value,
      audioEncoding: this.audioEncodingControl.value,
    });
    if (this.facade.audioProductionPlan()) {
      void this.liveAnnouncer.announce('Audio production plan ready', 'polite');
    } else if (this.facade.error()) {
      void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
    }
  }

  async generateAudio(): Promise<void> {
    if (!this.facade.audioProductionPlan() || this.renderRequests.length === 0) return;
    const projectId = await this.fullAudioGenerationService.generate(this.renderRequests, this.facade.currentProjectId() ?? undefined);
    if (projectId) {
      this.facade.setCurrentProjectId(projectId);
      this.lastKnownProjectId = projectId;
      await this.facade.finalizeAudioGeneration(projectId);
      if (this.facade.error()) {
        void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
        return;
      }
    }
    if (this.fullAudioGenerationService.audioUrl && !this.facade.error()) {
      void this.liveAnnouncer.announce('Audiobook preview is ready', 'polite');
    }
  }

  async generateAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    options: { fullRunId?: number } = {}
  ): Promise<void> {
    await this.renderRequestAudioService.generate(renderRequest, requestIndex, {
      ...options,
      projectId: this.facade.currentProjectId() ?? undefined,
    });

    const state = this.renderRequestAudioService.audioStates[requestIndex];
    if (state?.status === 'generated' && this.fullAudioGenerationService.audioUrl) {
      this.fullAudioGenerationService.markStale(
        'Audiobook preview needs regeneration because one or more parts changed.'
      );
    }
  }

  cancelRenderRequestGeneration(requestIndex: number): void {
    this.renderRequestAudioService.cancel(requestIndex);
  }

  cancelFullAudioGeneration(): void {
    this.fullAudioGenerationService.cancel();
  }

  async approveScript(): Promise<void> {
    await this.facade.approveScript();
  }

  displayLanguageCode(languageCode: string | null | undefined): string {
    if (!languageCode) {
      return 'Unknown';
    }
    return this.languageCodeOptions.find((option) => option.value === languageCode)?.label ?? languageCode;
  }

  private workflowState(): StudioWorkflowState {
    return {
      storyText: this.storyTextControl.value,
      castCount: this.facade.cast().length,
      castReviewed: this.facade.castReviewed(),
      scriptTurnCount: this.facade.scriptTurns().length,
      scriptApproved: this.facade.scriptApproved(),
      annotatedTurnCount: this.facade.annotatedTurns().length,
      performanceNotesStale: this.facade.performanceNotesStale(),
      performanceReady: this.facade.performanceReady(),
      audioProductionPlanReady: this.facade.audioProductionPlan() !== null,
      audioGenerated: this.fullPlanAudioUrl !== null && !this.fullPlanAudioStale,
      audioAssetsCount: this.facade.audioAssets().length,
    };
  }

  startProjectTitleEdit(): void {
    this.projectTitleControl.setValue(this.facade.projectTitle());
    this.projectTitleEditing = true;
  }

  async saveProjectTitle(): Promise<void> {
    await this.facade.saveProjectTitle(this.projectTitleControl.value);
    if (!this.facade.error()) {
      this.projectTitleControl.setValue(this.facade.projectTitle());
      this.projectTitleEditing = false;
    }
  }

  cancelProjectTitleEdit(): void {
    this.projectTitleControl.setValue(this.facade.projectTitle());
    this.projectTitleEditing = false;
  }

  startCastEdit(index: number): void {
    if (!this.castEditable) {
      return;
    }
    this.facade.startCastEdit(index);
  }
  async saveCastEdit(index: number): Promise<void> { await this.facade.saveCastEdit(index); }
  cancelCastEdit(): void { this.facade.cancelCastEdit(); }

  startAddSpeaker(): void {
    if (!this.castEditable) {
      return;
    }
    this.facade.startAddSpeaker();
  }
  async saveAddSpeaker(): Promise<void> { await this.facade.saveAddSpeaker(); }
  cancelAddSpeaker(): void { this.facade.cancelAddSpeaker(); }
  async removeSpeaker(index: number): Promise<void> { await this.facade.removeSpeaker(index); }

  openVoicePicker(index: number): void {
    if (!this.castEditable) {
      return;
    }
    this.voicePickerForNewSpeaker = false;
    this.voicePickerOpenForIndex = index;
  }

  openVoicePickerForNewSpeaker(): void {
    if (!this.castEditable) {
      return;
    }
    this.voicePickerOpenForIndex = null;
    this.voicePickerForNewSpeaker = true;
  }

  get voicePickerOpen(): boolean {
    return this.voicePickerOpenForIndex !== null || this.voicePickerForNewSpeaker;
  }

  closeVoicePicker(): void {
    this.voicePickerOpenForIndex = null;
    this.voicePickerForNewSpeaker = false;
  }

  voicePickerSpeakerName(): string {
    if (this.voicePickerForNewSpeaker) {
      return formatSpeakerDisplayName(this.facade.addSpeakerDraft()?.speakerName ?? '') || 'New speaker';
    }
    if (this.voicePickerOpenForIndex === null) return '';
    return formatSpeakerDisplayName(this.facade.cast()[this.voicePickerOpenForIndex]?.speakerName ?? '');
  }

  voicePickerCurrentVoiceId(): string {
    if (this.voicePickerForNewSpeaker) {
      return (this.facade.addSpeakerDraft()?.voiceSuggestion ?? '').toLowerCase();
    }
    if (this.voicePickerOpenForIndex === null) return '';
    return (this.facade.cast()[this.voicePickerOpenForIndex]?.voiceSuggestion ?? '').toLowerCase();
  }

  async applyVoiceSelection(voice: SpeakerVoiceCatalogItem): Promise<void> {
    const voiceKey = voice.id.toUpperCase();
    if (this.voicePickerForNewSpeaker) {
      this.facade.setAddSpeakerVoice(voiceKey);
      this.closeVoicePicker();
      return;
    }
    if (this.voicePickerOpenForIndex === null) return;
    this.cast[this.voicePickerOpenForIndex].voiceSuggestion = voiceKey;
    await this.facade.saveCastVoice(this.voicePickerOpenForIndex, voiceKey);
    this.closeVoicePicker();
  }

  startScriptTurnEdit(index: number): void { this.facade.startScriptTurnEdit(index); }
  async saveScriptTurnEdit(index: number): Promise<void> { await this.facade.saveScriptTurnEdit(index); }
  cancelScriptTurnEdit(): void { this.facade.cancelScriptTurnEdit(); }

  // ── Autopilot orchestration ───────────────────────────────────────────────

  setStudioMode(mode: StudioMode): void {
    this.studioMode.set(mode);
  }

  openInGuidedWorkflow(): void {
    const section = this.autopilot.guidedSectionForFailedStep();
    this.studioMode.set('guided');
    setTimeout(() => this.scrollService.scrollTo(section), 50);
  }

  // Clicking a progress step jumps into the Guided workflow at that step.
  openGuidedStep(stepId: AutopilotStepId): void {
    const section = this.autopilot.guidedSectionForStep(stepId);
    this.studioMode.set('guided');
    setTimeout(() => this.scrollService.scrollTo(section), 50);
  }

  async startAutopilot(): Promise<void> {
    this.autopilot.reset();
    setTimeout(() => this.scrollService.scrollTo('autopilot-progress'), 50);
    await this.runAutopilotFrom('analyze-story');
  }

  async retryAutopilot(): Promise<void> {
    const failed = this.autopilot.firstFailedStepId();
    if (failed) {
      await this.runAutopilotFrom(failed);
    }
  }

  private async runAutopilotFrom(startId: AutopilotStepId): Promise<void> {
    this.autopilotActive = true;
    this.autopilot.setRunning(true);
    try {
      const order = this.autopilot.stepOrder;
      for (let i = order.indexOf(startId); i < order.length; i++) {
        const id = order[i];
        this.autopilot.markRunning(id);
        const ok = await this.runAutopilotStep(id);
        if (!ok) {
          this.autopilot.markFailed(id, this.error ?? 'This step could not be completed.');
          return;
        }
        this.autopilot.markCompleted(id);
      }
      this.autopilot.markFinished();
    } finally {
      this.autopilotActive = false;
    }
  }

  private async runAutopilotStep(id: AutopilotStepId): Promise<boolean> {
    switch (id) {
      case 'analyze-story':
        await this.analyzeStory();
        return !this.error && this.cast.length > 0;
      case 'detect-cast':
        return !this.error && this.cast.length > 0;
      case 'assign-voices':
        await this.approveCast();
        return !this.error && this.castReviewed;
      case 'split-script':
        await this.createScriptPreview();
        if (this.error || this.scriptTurns.length === 0) return false;
        await this.approveScript();
        return !this.error && this.scriptApproved;
      case 'add-emotion':
        await this.createPerformanceNotes();
        return !this.error && this.performanceReady;
      case 'generate-preview':
        await this.createAudioProductionPlan();
        if (this.error || !this.audioProductionPlan) return false;
        await this.generateAudio();
        return !this.error && this.fullPlanAudioUrl !== null;
    }
  }

  // ── Presentation helpers ──────────────────────────────────────────────────

  markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
    return markupFor(turn);
  }

  renderRequestAudioState(requestIndex: number): RenderRequestAudioState {
    return this.renderRequestAudioService.getState(
      requestIndex,
      this.renderRequests[requestIndex],
      this.facade.cast()
    );
  }

  anyAudioLoading(): boolean {
    return this.fullAudioGenerationService.loading || this.renderRequestAudioService.anyLoading();
  }

  generatedPartCount(): number { return this.renderRequestAudioService.generatedCount(); }
  failedPartCount(): number { return this.renderRequestAudioService.failedCount(); }
  canceledPartCount(): number { return this.renderRequestAudioService.canceledCount(); }
  timedOutPartCount(): number { return this.renderRequestAudioService.timedOutCount(); }

  missingPartCount(): number {
    return this.renderRequestAudioService.missingCount(this.renderRequests.length);
  }

  partsToGenerateCount(): number {
    return this.missingPartCount() + this.failedPartCount() + this.canceledPartCount() + this.timedOutPartCount();
  }

  audiobookReadinessSummary(): string {
    const ready = this.generatedPartCount();
    const total = this.renderRequests.length;
    const missing = this.missingPartCount();
    const failed = this.failedPartCount();
    const canceled = this.canceledPartCount();
    const timedOut = this.timedOutPartCount();

    if (total === 0) return 'No audio parts prepared yet';

    if (ready === total) return `${ready} of ${total} parts ready - ready to create audiobook preview`;

    const details: string[] = [];
    if (missing > 0) details.push(`${missing} missing`);
    if (failed > 0) details.push(`${failed} failed`);
    if (canceled > 0) details.push(`${canceled} canceled`);
    if (timedOut > 0) details.push(`${timedOut} timed out`);
    return `${ready} of ${total} parts ready - ${details.join(', ')}`;
  }

  audiobookGenerationExplanation(): string {
    const remaining = this.partsToGenerateCount();
    if (remaining === 0) {
      return this.fullPlanAudioStale
        ? 'All parts are ready. Generate audiobook preview will rebuild the final preview from the latest parts.'
        : 'All parts are ready. Generate audiobook preview will merge them into the final preview.';
    }
    return `Generate audiobook preview will use the parts that are already ready and only generate the ${remaining} missing, canceled, failed, or timed-out ${remaining === 1 ? 'part' : 'parts'} before merging.`;
  }

  partStatusLabel(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    switch (state.status) {
      case 'generating': return `Generating... ${this.partElapsedLabel(requestIndex)}`;
      case 'generated': return 'Ready to listen';
      case 'failed': return 'Failed - retry this part';
      case 'canceled': return 'Canceled - retry available';
      case 'timed-out': return 'Timed out - retry available';
      default: return 'Not generated yet';
    }
  }

  partActionLabel(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.status === 'generating') return 'Cancel';
    if (state.status === 'failed' || state.status === 'canceled' || state.status === 'timed-out') return 'Retry this part';
    if (state.status === 'generated') return 'Regenerate part';
    return 'Generate this part';
  }

  partElapsedLabel(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.status !== 'generating' || state.startedAt === null) return '';
    return formatElapsedTime(Date.now() - state.startedAt);
  }

  currentGenerationStatusLabel(): string {
    const currentIndex = this.currentGeneratingRequestIndex();
    if (currentIndex !== null) {
      const readyCount = this.generatedPartCount();
      return `Generating part ${currentIndex + 1} of ${this.renderRequests.length} - ${readyCount} parts already ready`;
    }
    if (this.fullPlanAudioLoading) return 'Generating audiobook preview...';
    if (this.fullPlanAudioError) return this.fullPlanAudioError;
    if (this.fullPlanAudioStatusMessage) return this.fullPlanAudioStatusMessage;
    if (this.fullPlanAudioUrl && this.fullPlanAudioStale) return 'Audiobook preview needs regeneration after part updates.';
    if (this.fullPlanAudioUrl) return 'Audiobook preview ready.';
    return this.audiobookReadinessSummary();
  }

  currentGenerationDetails(): string {
    if (this.currentGeneratingRequestIndex() !== null) {
      return 'You can cancel the active part generation. Completed parts stay available, and canceled, timed-out, or failed parts can be retried.';
    }
    if (this.fullPlanAudioLoading) return this.audiobookGenerationExplanation();
    if (this.fullPlanAudioStale) return 'Regenerate the audiobook preview to rebuild the final MP3 from the latest part audio.';
    if (this.fullPlanAudioUrl) return 'You can play or download the current audiobook preview, or regenerate it after changing parts.';
    return this.audiobookGenerationExplanation();
  }

  currentGenerationActionLabel(): string {
    const currentIndex = this.currentGeneratingRequestIndex();
    if (this.fullPlanAudioLoading && currentIndex !== null) {
      return `Generating part ${currentIndex + 1} of ${this.renderRequests.length}`;
    }
    return this.fullPlanAudioStale ? 'Regenerate preview' : 'Generate preview';
  }

  currentGenerationCanCancel(): boolean {
    return this.fullPlanAudioLoading && this.currentGeneratingRequestIndex() !== null;
  }

  currentGenerationCancelLabel(): string {
    return this.fullPlanAudioLoading ? 'Cancel generation' : '';
  }

  downloadRenderRequestAudio(requestIndex: number): void {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.audioUrl && state.filename) {
      this.downloadBlobUrl(state.audioUrl, state.filename);
    }
  }

  downloadFullPlanAudio(): void {
    if (this.fullPlanAudioUrl && this.fullPlanAudioFilename) {
      this.downloadBlobUrl(this.fullPlanAudioUrl, this.fullPlanAudioFilename);
    }
  }

  currentGeneratingRequestIndex(): number | null {
    const entry = Object.entries(this.renderRequestAudioService.audioStates)
      .find(([, state]) => state.status === 'generating');
    return entry ? Number(entry[0]) : null;
  }

  fullPlanDurationLabel(): string { return durationLabelFor(this.waveSurferService.get('full')); }

  renderRequestDurationLabel(requestIndex: number): string {
    return durationLabelFor(this.waveSurferService.get(`part:${requestIndex}`));
  }

  toggleFullGeneratedAudio(): void { void this.waveSurferService.get('full')?.playPause(); }

  toggleRenderRequestAudio(requestIndex: number): void {
    void this.waveSurferService.get(`part:${requestIndex}`)?.playPause();
  }

  isLoading(action: string): boolean { return this.facade.loadingAction() === action; }

  displaySpeakerName(speakerName: string): string { return formatSpeakerDisplayName(speakerName); }

  speakerInitials(speakerName: string): string { return speakerInitials(speakerName); }

  castAccentClass(index: number): string { return `cast-accent-${index % this.speakerAccents.length}`; }

  speakerStyle(speakerName: string | null | undefined): Record<string, string> {
    const accent = this.speakerAccentFor(speakerName);
    return { '--speaker-accent': accent.color, '--speaker-accent-shadow': accent.shadow };
  }

  playVoiceSample(speakerName: string, event?: Event): void {
    event?.preventDefault();
    const displayName = this.displaySpeakerName(speakerName);
    const path = this.voiceSamplePathFor(displayName);
    this.waveSurferService.pauseAll();
    this.voiceSampleService.play(path, `voice:${displayName}`, () => this.scrollService.scrollTo('cast-section'));
  }

  isVoiceSamplePlaying(speakerName: string): boolean {
    return this.activeSampleKey === `voice:${this.displaySpeakerName(speakerName)}`;
  }

  voiceSamplePathFor(speakerName: string): string {
    const normalizedName = speakerName.toLowerCase();
    if (normalizedName.includes('mara')) return '/assets/audio/voice-samples/mara.mp3';
    if (normalizedName.includes('jonas')) return '/assets/audio/voice-samples/jonas.mp3';
    if (normalizedName.includes('station keeper')) return '/assets/audio/voice-samples/station-keeper.mp3';
    return '/assets/audio/voice-samples/narrator.mp3';
  }

  // ── Tracking ──────────────────────────────────────────────────────────────

  trackCastByIndex(index: number): number { return index; }

  trackScriptGroup(_: number, group: ScriptGroup): string {
    return `${group.speaker}:${group.turns[0]?.index ?? 0}`;
  }

  trackScriptTurn(_: number, indexedTurn: IndexedSpeakerSplitTurn): number { return indexedTurn.index; }

  trackSpeakerOption(_: number, speakerName: string): string { return speakerName; }

  renderRequestSpeakerName(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    return state.speaker ?? (state.voice ? `${this.displaySpeakerName(state.voice)} voice` : `Voice part ${requestIndex + 1}`);
  }

  renderRequestRoleLabel(requestIndex: number): string | null {
    const speakerName = this.renderRequestAudioState(requestIndex).speaker;
    return speakerName ? this.castMemberForSpeaker(speakerName)?.roleDescription ?? null : null;
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnDestroy(): void {
    this.fullAudioGenerationService.cancel();
    this.renderRequestAudioService.destroy();
    this.voiceSampleService.destroy();
    this.waveSurferService.destroyAll();
    this.fullAudioGenerationService.clearAudio();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['snapshot']?.currentValue) {
      this.hydrateFromSnapshot(changes['snapshot'].currentValue as AudiobookWorkflowSnapshotResponse);
      if (this.scrollToSectionAfterLoad) {
        this.pendingScrollToSection = this.scrollToSectionAfterLoad;
      }
    }
  }

  ngAfterViewInit(): void {
    this.facade.onAudioReset = () => {
      this.fullPlanAudioPlaying = false;
      this.renderRequestAudioPlayingStates = {};
    };

    if (this.pendingScrollToSection) {
      setTimeout(() => {
        this.scrollService.scrollTo(this.pendingScrollToSection!);
        this.pendingScrollToSection = null;
        this.scrollToSectionAfterLoad = null;
      }, 50);
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private downloadBlobUrl(url: string, filename: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  private speakerAccentFor(speakerName: string | null | undefined): SpeakerAccent {
    const key = normalizedSpeakerKey(speakerName ?? '');
    const names = this.knownSpeakerNames();
    const index = Math.max(0, names.findIndex((name) => normalizedSpeakerKey(name) === key));
    return this.speakerAccents[index % this.speakerAccents.length];
  }

  private knownSpeakerNames(): string[] {
    const names: string[] = [];
    const add = (speakerName: string | null | undefined) => {
      if (!speakerName) return;
      const displayName = formatSpeakerDisplayName(speakerName);
      if (!names.some((name) => normalizedSpeakerKey(name) === normalizedSpeakerKey(displayName))) {
        names.push(displayName);
      }
    };
    this.facade.cast().forEach((speaker) => add(speaker.speakerName));
    this.facade.scriptTurns().forEach((turn) => add(turn.speaker));
    this.facade.annotatedTurns().forEach((turn) => add(turn.speaker));
    return names.length > 0 ? names : this.heroCast.map((speaker) => speaker.name);
  }

  private castMemberForSpeaker(speakerName: string): SpeakerVoiceAnalysisItem | null {
    const key = normalizedSpeakerKey(speakerName);
    return this.facade.cast().find((speaker) => normalizedSpeakerKey(speaker.speakerName) === key) ?? null;
  }
}
