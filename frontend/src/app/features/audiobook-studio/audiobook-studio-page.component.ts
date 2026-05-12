import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, OnDestroy, ViewEncapsulation } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import {
  FinalTtsRequestPreview,
  SpeakerVoiceAnalysisItem,
} from '../audiobook-shared/service/audiobook-workflow.service';
import {
  SingleSpeakerRenderPlan,
  SingleSpeakerRenderRequest,
} from '../audiobook-shared/service/audiobook-api.service';
import { CastSectionComponent } from './components/cast-section/cast-section.component';
import { JourneyGridComponent } from './components/journey-grid/journey-grid.component';
import { PerformanceNotesComponent } from './components/performance-notes/performance-notes.component';
import { ScriptReviewComponent } from './components/script-review/script-review.component';
import { StoryInputComponent } from './components/story-input/story-input.component';
import { StudioHeroComponent } from './components/studio-hero/studio-hero.component';
import { WaveformPlayerComponent } from './components/waveform-player/waveform-player.component';
import { WorkflowProgressComponent } from './components/workflow-progress/workflow-progress.component';
import {
  BENEFIT_CHIPS,
  HERO_CAST,
  JOURNEY_STEPS,
  LANGUAGE_CODE_OPTIONS,
  MODEL_NAME_OPTIONS,
  SAMPLE_STORY,
  SPEAKER_ACCENTS
} from './data/studio-content';
import {
  AnnotatedMarkup,
  AnnotatedSpeakerTurn,
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

// Re-export so the spec can import formatSpeakerDisplayName from this file path unchanged.
export { formatSpeakerDisplayName };

@Component({
  selector: 'app-audiobook-studio-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    StudioHeroComponent,
    WaveformPlayerComponent,
    JourneyGridComponent,
    WorkflowProgressComponent,
    StoryInputComponent,
    CastSectionComponent,
    ScriptReviewComponent,
    PerformanceNotesComponent,
  ],
  providers: [
    AudiobookStudioFacade,
    WaveSurferService,
    VoiceSampleService,
    RenderRequestAudioService,
    FullAudioGenerationService,
    ScrollService,
  ],
  templateUrl: './audiobook-studio-page.component.html',
  styleUrl: './audiobook-studio-page.component.css',
  encapsulation: ViewEncapsulation.None,
})
export class AudiobookStudioPageComponent implements AfterViewInit, OnDestroy {
  // ── Spec proxy: partGenerationTimeoutMs ───────────────────────────────────
  get partGenerationTimeoutMs(): number { return this.renderRequestAudioService.partGenerationTimeoutMs; }
  set partGenerationTimeoutMs(ms: number) { this.renderRequestAudioService.partGenerationTimeoutMs = ms; }

  readonly speakerStyleFn = (name: string | null | undefined) => this.speakerStyle(name);

  readonly benefitChips = BENEFIT_CHIPS;
  readonly heroCast: readonly HeroCastMember[] = HERO_CAST;
  readonly journeySteps = JOURNEY_STEPS;
  readonly speakerAccents: readonly SpeakerAccent[] = SPEAKER_ACCENTS;
  readonly sampleStory = SAMPLE_STORY;
  readonly languageCodeOptions = LANGUAGE_CODE_OPTIONS;
  readonly modelNameOptions = MODEL_NAME_OPTIONS;

  storyTextControl = new FormControl('', { nonNullable: true });
  promptControl = new FormControl('An immersive audiobook performance with a clear narrator and distinct character voices.', { nonNullable: true });
  languageCodeControl = new FormControl('en-US', { nonNullable: true });
  modelNameControl = new FormControl('gemini-3.1-flash-tts-preview', { nonNullable: true });
  audioEncodingControl = new FormControl('MP3', { nonNullable: true });

  // ── Facade state proxies (spec reads/writes these directly) ───────────────

  get cast(): SpeakerVoiceAnalysisItem[] { return this.facade.cast(); }
  set cast(value: SpeakerVoiceAnalysisItem[]) { this.facade.setCast(value); }

  get scriptTurns(): SpeakerSplitTurn[] { return this.facade.scriptTurns(); }
  set scriptTurns(value: SpeakerSplitTurn[]) { this.facade.setScriptTurns(value); }

  get annotatedTurns(): AnnotatedSpeakerTurn[] { return this.facade.annotatedTurns(); }
  set annotatedTurns(value: AnnotatedSpeakerTurn[]) { this.facade.setAnnotatedTurns(value); }

  get finalRequest(): FinalTtsRequestPreview | null { return this.facade.finalRequest(); }
  set finalRequest(value: FinalTtsRequestPreview | null) { this.facade.setFinalRequest(value); }

  get audioProductionPlan(): SingleSpeakerRenderPlan | null { return this.facade.audioProductionPlan(); }
  set audioProductionPlan(value: SingleSpeakerRenderPlan | null) { this.facade.setAudioProductionPlan(value); }

  get castReviewed(): boolean { return this.facade.castReviewed(); }
  set castReviewed(value: boolean) { this.facade.setCastReviewed(value); }

  get scriptApproved(): boolean { return this.facade.scriptApproved(); }
  set scriptApproved(value: boolean) { this.facade.setScriptApproved(value); }

  get performanceNotesStale(): boolean { return this.facade.performanceNotesStale(); }
  set performanceNotesStale(value: boolean) { this.facade.setPerformanceNotesStale(value); }

  get loadingAction(): string | null { return this.facade.loadingAction(); }
  get error(): string | null { return this.facade.error(); }

  get editingCastIndex(): number | null { return this.facade.editingCastIndex(); }
  get castEditDraft(): SpeakerVoiceAnalysisItem | null { return this.facade.castEditDraft(); }
  get editingScriptTurnIndex(): number | null { return this.facade.editingScriptTurnIndex(); }
  get scriptTurnEditDraft(): SpeakerSplitTurn | null { return this.facade.scriptTurnEditDraft(); }

  // ── Full-audio delegated state ────────────────────────────────────────────
  get fullPlanAudioLoading(): boolean { return this.fullAudioGenerationService.loading; }
  get fullPlanAudioError(): string | null { return this.fullAudioGenerationService.error; }
  get fullPlanAudioStale(): boolean { return this.fullAudioGenerationService.stale; }
  get fullPlanAudioStatusMessage(): string | null { return this.fullAudioGenerationService.statusMessage; }
  get fullPlanAudioUrl(): string | null { return this.fullAudioGenerationService.audioUrl; }
  get fullPlanAudioFilename(): string | null { return this.fullAudioGenerationService.filename; }

  // ── Voice-sample delegated state ──────────────────────────────────────────
  get activeSampleKey(): string | null { return this.voiceSampleService.activeSampleKey; }

  fullPlanAudioPlaying = false;
  renderRequestAudioPlayingStates: Record<number, boolean> = {};

  constructor(
    private readonly facade: AudiobookStudioFacade,
    private readonly waveSurferService: WaveSurferService,
    private readonly voiceSampleService: VoiceSampleService,
    private readonly renderRequestAudioService: RenderRequestAudioService,
    private readonly fullAudioGenerationService: FullAudioGenerationService,
    private readonly scrollService: ScrollService,
    private readonly liveAnnouncer: LiveAnnouncer,
  ) {}

  // ── Computed from facade signals ──────────────────────────────────────────

  get scriptGroups(): ScriptGroup[] { return this.facade.scriptGroups(); }
  get speakerOptions(): string[] { return this.facade.speakerOptions(); }

  get renderRequests(): SingleSpeakerRenderRequest[] {
    return this.facade.audioProductionPlan()?.renderRequests ?? [];
  }

  get finalRequestJson(): string {
    return this.facade.finalRequest() ? JSON.stringify(this.facade.finalRequest(), null, 2) : '';
  }

  get audioProductionPlanJson(): string {
    return this.facade.audioProductionPlan() ? JSON.stringify(this.facade.audioProductionPlan(), null, 2) : '';
  }

  get workflowSteps(): WorkflowStep[] {
    const storyAdded = this.storyTextControl.value.trim().length > 0;
    const castDetected = this.facade.cast().length > 0;
    const scriptReady = this.facade.scriptTurns().length > 0;
    const performanceReady = this.facade.annotatedTurns().length > 0 && !this.facade.performanceNotesStale();
    const audioReady = this.fullPlanAudioUrl !== null;

    return [
      {
        key: 'story',
        label: 'Story',
        sectionId: 'story-section',
        status: storyAdded ? 'completed' : 'current',
        statusLabel: storyAdded ? 'Story added' : 'Add story'
      },
      {
        key: 'cast',
        label: 'Cast',
        sectionId: 'cast-section',
        status: !storyAdded ? 'locked' : this.facade.castReviewed() ? 'completed' : castDetected ? 'warning' : 'current',
        statusLabel: !storyAdded ? 'Locked' : this.facade.castReviewed() ? 'Cast approved' : castDetected ? 'Cast needs review' : 'Find characters'
      },
      {
        key: 'script',
        label: 'Script',
        sectionId: 'script-section',
        status: !this.facade.castReviewed() && !scriptReady ? 'locked' : this.facade.scriptApproved() ? 'completed' : scriptReady ? 'warning' : 'current',
        statusLabel: !this.facade.castReviewed() && !scriptReady ? 'Locked' : this.facade.scriptApproved() ? 'Script approved' : scriptReady ? 'Script needs review' : 'Review script'
      },
      {
        key: 'performance',
        label: 'Performance',
        sectionId: 'performance-section',
        status: !this.facade.scriptApproved() ? 'locked' : this.facade.performanceNotesStale() ? 'warning' : performanceReady ? 'completed' : 'current',
        statusLabel: !this.facade.scriptApproved() ? 'Locked' : this.facade.performanceNotesStale() ? 'Notes stale' : performanceReady ? 'Performance ready' : 'Add emotion'
      },
      {
        key: 'audio',
        label: 'Audio',
        sectionId: 'audio-section',
        status: !performanceReady ? 'locked' : audioReady ? 'completed' : 'current',
        statusLabel: !performanceReady ? 'Locked' : audioReady ? 'Preview ready' : this.facade.audioProductionPlan() ? 'Generate preview' : 'Prepare audio'
      }
    ];
  }

  get currentTask(): CurrentTask {
    if (this.storyTextControl.value.trim().length === 0) {
      return {
        title: 'Current task: Start with your story',
        body: 'Paste your text or use the sample story. TTS Lab will find the narrator and characters for you.',
        nextAction: 'Paste text or use the sample story, then click Find narrator & characters.',
        sectionId: 'story-section'
      };
    }

    if (this.facade.cast().length === 0) {
      return {
        title: 'Current task: Find narrator & characters',
        body: 'TTS Lab will detect who is speaking in your story and suggest matching voices.',
        nextAction: 'Click Find narrator & characters to get started.',
        sectionId: 'story-section'
      };
    }

    if (!this.facade.castReviewed()) {
      return {
        title: 'Current task: Choose your voices',
        body: 'We found the speakers in your story. Preview each voice or change it to something that fits better.',
        nextAction: 'Approve the voices or make changes, then continue to review the script.',
        sectionId: 'cast-section'
      };
    }

    if (this.facade.scriptTurns().length === 0) {
      return {
        title: 'Current task: Creating the script',
        body: 'TTS Lab is preparing your story, splitting it into speaker turns so every line can be performed by the right voice.',
        nextAction: 'Click Next: Review script when ready.',
        sectionId: 'cast-section'
      };
    }

    if (!this.facade.scriptApproved()) {
      return {
        title: 'Current task: Check the script',
        body: 'Each line has been assigned to a speaker. Verify this looks correct.',
        nextAction: 'Approve the script or go back to adjust voices.',
        sectionId: 'script-section'
      };
    }

    if (this.facade.annotatedTurns().length === 0 || this.facade.performanceNotesStale()) {
      return {
        title: 'Current task: Add emotion & pacing',
        body: 'Fine-tune how each line should sound. Add notes like calm, urgent, or whispered to guide the voice generation.',
        nextAction: this.facade.performanceNotesStale() ? 'Update emotion & pacing, then continue.' : 'Add emotion notes or skip if you\'re ready to generate.',
        sectionId: 'performance-section'
      };
    }

    if (!this.facade.audioProductionPlan()) {
      return {
        title: 'Current task: Preparing audiobook',
        body: 'TTS Lab is converting your script and performance notes into an audiobook, ready to preview.',
        nextAction: 'Click Next: Prepare audiobook when ready.',
        sectionId: 'performance-section'
      };
    }

    return {
      title: 'Current task: Generate your audiobook preview',
      body: 'Your script, voices, and performance notes are ready. Generate a preview and listen before downloading.',
      nextAction: 'Generate a preview to hear how it sounds. You can always edit and regenerate.',
      sectionId: 'audio-section'
    };
  }

  // ── User actions ──────────────────────────────────────────────────────────

  useSampleStory(): void {
    this.storyTextControl.setValue(this.sampleStory);
    this.facade.resetPipeline();
  }

  focusStoryInput(event?: Event): void {
    event?.preventDefault();
    this.scrollService.scrollTo('story-section');
    this.scrollService.focusById('story-text', { preventScroll: true });
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

  async createScriptPreviewAndScroll(): Promise<void> {
    await this.createScriptPreview();
    if (this.facade.scriptTurns().length > 0) {
      this.scrollService.scrollTo('script-section');
    }
  }

  approveScriptAndScroll(): void {
    this.approveScript();
    this.scrollService.scrollTo('performance-section');
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
    await this.facade.analyzeStory(this.storyTextControl.value);
    if (this.facade.cast().length > 0) {
      void this.liveAnnouncer.announce(`Found ${this.facade.cast().length} characters`, 'polite');
    } else if (this.facade.error()) {
      void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
    }
  }

  async createScriptPreview(): Promise<void> {
    await this.facade.createScriptPreview(this.storyTextControl.value);
    if (this.facade.scriptTurns().length > 0) {
      void this.liveAnnouncer.announce(`Script ready with ${this.facade.scriptTurns().length} turns`, 'polite');
    } else if (this.facade.error()) {
      void this.liveAnnouncer.announce(this.facade.error()!, 'assertive');
    }
  }

  async createPerformanceNotes(): Promise<void> {
    await this.facade.createPerformanceNotes();
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
    const projectId = await this.fullAudioGenerationService.generate(this.renderRequests);
    if (projectId) {
      this.facade.setCurrentProjectId(projectId);
    }
    if (this.fullAudioGenerationService.audioUrl) {
      void this.liveAnnouncer.announce('Audiobook preview is ready', 'polite');
    }
  }

  async generateAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    options: { fullRunId?: number } = {}
  ): Promise<void> {
    await this.renderRequestAudioService.generate(renderRequest, requestIndex, options);

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

  approveScript(): void {
    this.facade.approveScript();
  }

  startCastEdit(index: number): void { this.facade.startCastEdit(index); }
  saveCastEdit(index: number): void { this.facade.saveCastEdit(index); }
  cancelCastEdit(): void { this.facade.cancelCastEdit(); }

  startScriptTurnEdit(index: number): void { this.facade.startScriptTurnEdit(index); }
  saveScriptTurnEdit(index: number): void { this.facade.saveScriptTurnEdit(index); }
  cancelScriptTurnEdit(): void { this.facade.cancelScriptTurnEdit(); }

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

  ngAfterViewInit(): void {
    this.facade.onAudioReset = () => {
      this.fullPlanAudioPlaying = false;
      this.renderRequestAudioPlayingStates = {};
    };
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
