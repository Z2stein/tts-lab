import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, OnDestroy, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import WaveSurfer from 'wavesurfer.js';
import {
  FinalTtsRequestPreview,
  SingleSpeakerRenderPlan,
  SingleSpeakerRenderRequest,
  SpeakerVoiceAnalysisItem,
  TtsWorkbenchService
} from '../tts-workbench/tts-workbench.service';
import { CastSectionComponent } from './components/cast-section/cast-section.component';
import { JourneyGridComponent } from './components/journey-grid/journey-grid.component';
import { PerformanceNotesComponent } from './components/performance-notes/performance-notes.component';
import { ScriptReviewComponent } from './components/script-review/script-review.component';
import { StoryInputComponent } from './components/story-input/story-input.component';
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
  WorkflowStepKey,
  WorkflowStepStatus
} from './models/audiobook-studio.types';
import { durationLabelFor, formatElapsedTime } from './utils/audio-format';
import { markupFor } from './utils/annotated-markup';
import { formatSpeakerDisplayName, normalizedSpeakerKey, speakerInitials } from './utils/speaker-name';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from './services/render-request-audio.service';
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
    JourneyGridComponent,
    WorkflowProgressComponent,
    StoryInputComponent,
    CastSectionComponent,
    ScriptReviewComponent,
    PerformanceNotesComponent
  ],
  providers: [
    WaveSurferService,
    VoiceSampleService,
    RenderRequestAudioService,
    FullAudioGenerationService,
  ],
  templateUrl: './audiobook-studio-page.component.html',
  styleUrl: './audiobook-studio-page.component.css'
})
export class AudiobookStudioPageComponent implements AfterViewInit, OnDestroy {
  // Proxy to service so spec can write component.partGenerationTimeoutMs = 5
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

  cast: SpeakerVoiceAnalysisItem[] = [];
  scriptTurns: SpeakerSplitTurn[] = [];
  annotatedTurns: AnnotatedSpeakerTurn[] = [];
  finalRequest: FinalTtsRequestPreview | null = null;
  audioProductionPlan: SingleSpeakerRenderPlan | null = null;
  loadingAction: string | null = null;
  error: string | null = null;
  editingCastIndex: number | null = null;
  castEditDraft: SpeakerVoiceAnalysisItem | null = null;
  editingScriptTurnIndex: number | null = null;
  scriptTurnEditDraft: SpeakerSplitTurn | null = null;
  castReviewed = false;
  scriptApproved = false;
  performanceNotesStale = false;
  demoPlaying = false;
  fullPlanAudioPlaying = false;
  renderRequestAudioPlayingStates: Record<number, boolean> = {};

  // ── Full-audio delegated state (spec reads these directly) ────────────────
  get fullPlanAudioLoading(): boolean { return this.fullAudioGenerationService.loading; }
  get fullPlanAudioError(): string | null { return this.fullAudioGenerationService.error; }
  get fullPlanAudioStale(): boolean { return this.fullAudioGenerationService.stale; }
  get fullPlanAudioStatusMessage(): string | null { return this.fullAudioGenerationService.statusMessage; }
  get fullPlanAudioUrl(): string | null { return this.fullAudioGenerationService.audioUrl; }
  get fullPlanAudioFilename(): string | null { return this.fullAudioGenerationService.filename; }

  // ── Voice-sample delegated state ──────────────────────────────────────────
  get activeSampleKey(): string | null { return this.voiceSampleService.activeSampleKey; }

  private demoWaveformElement: ElementRef<HTMLElement> | null = null;
  private fullWaveformElement: ElementRef<HTMLElement> | null = null;

  @ViewChild('demoWaveform')
  set demoWaveform(ref: ElementRef<HTMLElement> | undefined) {
    this.demoWaveformElement = ref ?? null;
    this.initializeDemoWaveform();
  }

  @ViewChild('fullWaveform')
  set fullWaveform(ref: ElementRef<HTMLElement> | undefined) {
    this.fullWaveformElement = ref ?? null;
    this.initializeFullWaveform();
  }

  @ViewChildren('renderRequestWaveform')
  renderRequestWaveformElements!: QueryList<ElementRef<HTMLElement>>;

  constructor(
    private readonly ttsWorkbenchService: TtsWorkbenchService,
    private readonly waveSurferService: WaveSurferService,
    private readonly voiceSampleService: VoiceSampleService,
    private readonly renderRequestAudioService: RenderRequestAudioService,
    private readonly fullAudioGenerationService: FullAudioGenerationService,
  ) {}

  get scriptGroups(): ScriptGroup[] {
    return this.scriptTurns.reduce<ScriptGroup[]>((groups, turn, index) => {
      const lastGroup = groups[groups.length - 1];
      if (lastGroup?.speaker === turn.speaker) {
        lastGroup.turns.push({ index, turn });
      } else {
        groups.push({ speaker: turn.speaker, turns: [{ index, turn }] });
      }
      return groups;
    }, []);
  }

  get speakerOptions(): string[] {
    const speakers = new Set(this.cast.map((speaker) => speaker.speakerName).filter(Boolean));
    const hasNarrator = this.scriptTurns.some((turn) => turn.speaker.toLowerCase() === 'narrator') ||
      this.cast.some((speaker) => speaker.speakerName.toLowerCase() === 'narrator');

    if (hasNarrator) {
      speakers.add('Narrator');
    }

    if (this.scriptTurnEditDraft?.speaker) {
      speakers.add(this.scriptTurnEditDraft.speaker);
    }

    return Array.from(speakers);
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

  get workflowSteps(): WorkflowStep[] {
    const storyAdded = this.storyTextControl.value.trim().length > 0;
    const castDetected = this.cast.length > 0;
    const scriptReady = this.scriptTurns.length > 0;
    const performanceReady = this.annotatedTurns.length > 0 && !this.performanceNotesStale;
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
        status: !storyAdded ? 'locked' : this.castReviewed ? 'completed' : castDetected ? 'warning' : 'current',
        statusLabel: !storyAdded ? 'Locked' : this.castReviewed ? 'Cast approved' : castDetected ? 'Cast needs review' : 'Find characters'
      },
      {
        key: 'script',
        label: 'Script',
        sectionId: 'script-section',
        status: !this.castReviewed && !scriptReady ? 'locked' : this.scriptApproved ? 'completed' : scriptReady ? 'warning' : 'current',
        statusLabel: !this.castReviewed && !scriptReady ? 'Locked' : this.scriptApproved ? 'Script approved' : scriptReady ? 'Script needs review' : 'Review script'
      },
      {
        key: 'performance',
        label: 'Performance',
        sectionId: 'performance-section',
        status: !this.scriptApproved ? 'locked' : this.performanceNotesStale ? 'warning' : performanceReady ? 'completed' : 'current',
        statusLabel: !this.scriptApproved ? 'Locked' : this.performanceNotesStale ? 'Notes stale' : performanceReady ? 'Performance ready' : 'Add emotion'
      },
      {
        key: 'audio',
        label: 'Audio',
        sectionId: 'audio-section',
        status: !performanceReady ? 'locked' : audioReady ? 'completed' : 'current',
        statusLabel: !performanceReady ? 'Locked' : audioReady ? 'Preview ready' : this.audioProductionPlan ? 'Generate preview' : 'Prepare audio'
      }
    ];
  }

  get currentTask(): CurrentTask {
    if (this.storyTextControl.value.trim().length === 0) {
      return {
        title: 'Current task: Add your story',
        body: 'Paste a chapter or scene with narration and dialogue. This gives the studio enough material to discover speakers.',
        nextAction: 'Paste text or use the sample story, then choose Find characters.',
        sectionId: 'story-section'
      };
    }

    if (this.cast.length === 0) {
      return {
        title: 'Current task: Find characters',
        body: 'The studio will detect the narrator and speaking characters, then suggest voice directions for each one.',
        nextAction: 'Choose Find characters to build the cast.',
        sectionId: 'story-section'
      };
    }

    if (!this.castReviewed) {
      return {
        title: 'Current task: Review the cast',
        body: 'Check each character and preview the closest matching sample voice before the script is created.',
        nextAction: 'Edit any voice card that needs cleanup, then choose Review script.',
        sectionId: 'cast-section'
      };
    }

    if (this.scriptTurns.length === 0) {
      return {
        title: 'Current task: Create the script preview',
        body: 'The story will be split into speaker turns so every line can be performed by the right voice.',
        nextAction: 'Choose Review script to inspect the scene line by line.',
        sectionId: 'cast-section'
      };
    }

    if (!this.scriptApproved) {
      return {
        title: 'Current task: Review the script',
        body: 'Check that every line is assigned to the correct speaker. The generated audio uses these speaker assignments.',
        nextAction: 'Edit any incorrect turn, then choose Approve script.',
        sectionId: 'script-section'
      };
    }

    if (this.annotatedTurns.length === 0 || this.performanceNotesStale) {
      return {
        title: 'Current task: Add emotion and pacing',
        body: 'Performance notes add emotional intent and pauses so the audiobook sounds directed instead of flat.',
        nextAction: this.performanceNotesStale ? 'Regenerate emotion and pacing before preparing audio.' : 'Choose Add emotion & pacing.',
        sectionId: 'performance-section'
      };
    }

    if (!this.audioProductionPlan) {
      return {
        title: 'Current task: Prepare audio generation',
        body: 'The studio will convert your reviewed script and performance notes into voice parts ready for MP3 generation.',
        nextAction: 'Choose Prepare audio generation.',
        sectionId: 'performance-section'
      };
    }

    return {
      title: 'Current task: Generate and listen',
      body: 'Create the audiobook preview, then listen directly in the page or generate individual voice parts as needed.',
      nextAction: 'Choose Generate audiobook preview.',
      sectionId: 'audio-section'
    };
  }

  useSampleStory(): void {
    this.storyTextControl.setValue(this.sampleStory);
    this.resetPipeline();
    this.error = null;
  }

  focusStoryInput(event?: Event): void {
    event?.preventDefault();
    this.scrollToSection('story-section');
    const storyTextArea = document.getElementById('story-text') as HTMLTextAreaElement | null;
    storyTextArea?.focus({ preventScroll: true });
  }

  playDemo(event?: Event): void {
    event?.preventDefault();
    const demoWs = this.waveSurferService.get('demo');
    if (demoWs) {
      void demoWs.playPause();
      return;
    }
    this.playAudioPath('/assets/audio/voice-samples/full-text-preview.mp3', 'demo:fallback');
  }

  scrollToSection(sectionId: string, event?: Event): void {
    event?.preventDefault();
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async analyzeStoryAndScroll(): Promise<void> {
    await this.analyzeStory();
    if (this.cast.length > 0) {
      this.scrollToSection('cast-section');
    }
  }

  async createScriptPreviewAndScroll(): Promise<void> {
    await this.createScriptPreview();
    if (this.scriptTurns.length > 0) {
      this.scrollToSection('script-section');
    }
  }

  approveScriptAndScroll(): void {
    this.approveScript();
    this.scrollToSection('performance-section');
  }

  async createPerformanceNotesAndScroll(): Promise<void> {
    await this.createPerformanceNotes();
    if (this.annotatedTurns.length > 0) {
      this.scrollToSection('performance-section');
    }
  }

  async createAudioProductionPlanAndScroll(): Promise<void> {
    await this.createAudioProductionPlan();
    if (this.audioProductionPlan) {
      this.scrollToSection('audio-section');
    }
  }

  async analyzeStory(): Promise<void> {
    await this.runStep('cast', async () => {
      this.cast = await this.ttsWorkbenchService.analyzeSpeakers(this.storyTextControl.value);
      this.scriptTurns = [];
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.castReviewed = false;
      this.scriptApproved = false;
      this.performanceNotesStale = false;
      this.cancelCastEdit();
      this.cancelScriptTurnEdit();
      this.resetAudioStates();
    }, 'Story analysis failed.');
  }

  async createScriptPreview(): Promise<void> {
    await this.runStep('script', async () => {
      this.scriptTurns = await this.ttsWorkbenchService.splitDialogue(this.storyTextControl.value, this.cast);
      this.annotatedTurns = [];
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.castReviewed = true;
      this.scriptApproved = false;
      this.performanceNotesStale = false;
      this.cancelCastEdit();
      this.cancelScriptTurnEdit();
      this.resetAudioStates();
    }, 'Script preview failed.');
  }

  async createPerformanceNotes(): Promise<void> {
    await this.runStep('notes', async () => {
      this.annotatedTurns = await this.ttsWorkbenchService.annotateEmotions(this.scriptTurns);
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.performanceNotesStale = false;
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
    if (!this.audioProductionPlan || this.renderRequests.length === 0) return;
    await this.fullAudioGenerationService.generate(this.renderRequests);
    if (this.fullAudioGenerationService.audioUrl) {
      window.setTimeout(() => this.initializeFullWaveform());
    }
  }

  async generateAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    options: { fullRunId?: number } = {}
  ): Promise<void> {
    await this.renderRequestAudioService.generate(renderRequest, requestIndex, options);

    const state = this.renderRequestAudioService.audioStates[requestIndex];
    if (state?.status === 'generated') {
      if (this.fullAudioGenerationService.audioUrl) {
        this.fullAudioGenerationService.markStale(
          'Audiobook preview needs regeneration because one or more parts changed.'
        );
      } else {
        // Clear any leftover status message from a previous run
        if (this.fullAudioGenerationService.statusMessage === null) {
          // nothing to do
        }
      }
    }
  }

  cancelRenderRequestGeneration(requestIndex: number): void {
    this.renderRequestAudioService.cancel(requestIndex);
  }

  cancelFullAudioGeneration(): void {
    this.fullAudioGenerationService.cancel();
  }

  markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
    return markupFor(turn);
  }

  renderRequestAudioState(requestIndex: number): RenderRequestAudioState {
    return this.renderRequestAudioService.getState(
      requestIndex,
      this.renderRequests[requestIndex],
      this.cast
    );
  }

  anyAudioLoading(): boolean {
    return this.fullAudioGenerationService.loading || this.renderRequestAudioService.anyLoading();
  }

  generatedPartCount(): number {
    return this.renderRequestAudioService.generatedCount();
  }

  failedPartCount(): number {
    return this.renderRequestAudioService.failedCount();
  }

  canceledPartCount(): number {
    return this.renderRequestAudioService.canceledCount();
  }

  timedOutPartCount(): number {
    return this.renderRequestAudioService.timedOutCount();
  }

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

    if (total === 0) {
      return 'No audio parts prepared yet';
    }

    if (ready === total) {
      return `${ready} of ${total} parts ready - ready to create audiobook preview`;
    }

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
      case 'generating':
        return `Generating... ${this.partElapsedLabel(requestIndex)}`;
      case 'generated':
        return 'Ready to listen';
      case 'failed':
        return 'Failed - retry this part';
      case 'canceled':
        return 'Canceled - retry available';
      case 'timed-out':
        return 'Timed out - retry available';
      default:
        return 'Not generated yet';
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

    if (this.fullPlanAudioUrl && this.fullPlanAudioStale) {
      return 'Audiobook preview needs regeneration after part updates.';
    }

    if (this.fullPlanAudioUrl) return 'Audiobook preview ready.';
    return this.audiobookReadinessSummary();
  }

  currentGenerationDetails(): string {
    if (this.currentGeneratingRequestIndex() !== null) {
      return 'You can cancel the active part generation. Completed parts stay available, and canceled, timed-out, or failed parts can be retried.';
    }

    if (this.fullPlanAudioLoading) return this.audiobookGenerationExplanation();

    if (this.fullPlanAudioStale) {
      return 'Regenerate the audiobook preview to rebuild the final MP3 from the latest part audio.';
    }

    if (this.fullPlanAudioUrl) {
      return 'You can play or download the current audiobook preview, or regenerate it after changing parts.';
    }

    return this.audiobookGenerationExplanation();
  }

  currentGenerationActionLabel(): string {
    const currentIndex = this.currentGeneratingRequestIndex();
    if (this.fullPlanAudioLoading && currentIndex !== null) {
      return `Generating part ${currentIndex + 1} of ${this.renderRequests.length}`;
    }
    return this.fullPlanAudioStale ? 'Rebuild audiobook preview' : 'Generate audiobook preview';
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

  fullPlanDurationLabel(): string {
    return durationLabelFor(this.waveSurferService.get('full'));
  }

  renderRequestDurationLabel(requestIndex: number): string {
    return durationLabelFor(this.waveSurferService.get(`part:${requestIndex}`));
  }

  toggleFullGeneratedAudio(): void {
    void this.waveSurferService.get('full')?.playPause();
  }

  toggleRenderRequestAudio(requestIndex: number): void {
    void this.waveSurferService.get(`part:${requestIndex}`)?.playPause();
  }

  isLoading(action: string): boolean {
    return this.loadingAction === action;
  }

  displaySpeakerName(speakerName: string): string {
    return formatSpeakerDisplayName(speakerName);
  }

  speakerInitials(speakerName: string): string {
    return speakerInitials(speakerName);
  }

  castAccentClass(index: number): string {
    return `cast-accent-${index % this.speakerAccents.length}`;
  }

  speakerStyle(speakerName: string | null | undefined): Record<string, string> {
    const accent = this.speakerAccentFor(speakerName);
    return {
      '--speaker-accent': accent.color,
      '--speaker-accent-shadow': accent.shadow
    };
  }

  playVoiceSample(speakerName: string, event?: Event): void {
    event?.preventDefault();
    const displayName = this.displaySpeakerName(speakerName);
    const path = this.voiceSamplePathFor(displayName);
    this.waveSurferService.pauseAll();
    this.voiceSampleService.play(path, `voice:${displayName}`, () => this.scrollToSection('cast-section'));
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

  startCastEdit(index: number): void {
    this.editingCastIndex = index;
    this.castEditDraft = { ...this.cast[index] };
  }

  saveCastEdit(index: number): void {
    if (!this.castEditDraft) return;
    const draft: SpeakerVoiceAnalysisItem = { ...this.castEditDraft };
    this.cast = this.cast.map((speaker, speakerIndex) => speakerIndex === index ? draft : speaker);
    this.cancelCastEdit();
  }

  cancelCastEdit(): void {
    this.editingCastIndex = null;
    this.castEditDraft = null;
  }

  startScriptTurnEdit(index: number): void {
    this.editingScriptTurnIndex = index;
    this.scriptTurnEditDraft = { ...this.scriptTurns[index] };
  }

  saveScriptTurnEdit(index: number): void {
    if (!this.scriptTurnEditDraft) return;
    const draft: SpeakerSplitTurn = { ...this.scriptTurnEditDraft };
    this.scriptTurns = this.scriptTurns.map((turn, turnIndex) => turnIndex === index ? draft : turn);
    this.cancelScriptTurnEdit();
    this.scriptApproved = false;

    if (this.annotatedTurns.length > 0) {
      this.performanceNotesStale = true;
      this.finalRequest = null;
      this.audioProductionPlan = null;
      this.resetAudioStates();
    }
  }

  cancelScriptTurnEdit(): void {
    this.editingScriptTurnIndex = null;
    this.scriptTurnEditDraft = null;
  }

  approveScript(): void {
    this.scriptApproved = true;
  }

  ngOnDestroy(): void {
    this.fullAudioGenerationService.cancel();
    this.renderRequestAudioService.destroy();
    this.voiceSampleService.destroy();
    this.waveSurferService.destroyAll();
    this.fullAudioGenerationService.clearAudio();
  }

  ngAfterViewInit(): void {
    this.fullAudioGenerationService.onAudioReady = () => {
      window.setTimeout(() => this.initializeFullWaveform());
    };
    this.renderRequestAudioService.onPartReady = (requestIndex: number) => {
      this.waveSurferService.destroy(`part:${requestIndex}`);
      this.renderRequestAudioPlayingStates[requestIndex] = false;
      window.setTimeout(() => this.initializeRenderRequestWaveforms());
    };
    this.initializeDemoWaveform();
    this.renderRequestWaveformElements.changes.subscribe(() => this.initializeRenderRequestWaveforms());
  }

  trackCastByIndex(index: number): number {
    return index;
  }

  trackScriptGroup(_: number, group: ScriptGroup): string {
    return `${group.speaker}:${group.turns[0]?.index ?? 0}`;
  }

  trackScriptTurn(_: number, indexedTurn: IndexedSpeakerSplitTurn): number {
    return indexedTurn.index;
  }

  trackSpeakerOption(_: number, speakerName: string): string {
    return speakerName;
  }

  renderRequestSpeakerName(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    return state.speaker ?? (state.voice ? `${this.displaySpeakerName(state.voice)} voice` : `Voice part ${requestIndex + 1}`);
  }

  renderRequestRoleLabel(requestIndex: number): string | null {
    const speakerName = this.renderRequestAudioState(requestIndex).speaker;
    return speakerName ? this.castMemberForSpeaker(speakerName)?.roleDescription ?? null : null;
  }

  private resetPipeline(): void {
    this.cast = [];
    this.scriptTurns = [];
    this.annotatedTurns = [];
    this.finalRequest = null;
    this.audioProductionPlan = null;
    this.castReviewed = false;
    this.scriptApproved = false;
    this.performanceNotesStale = false;
    this.cancelCastEdit();
    this.cancelScriptTurnEdit();
    this.resetAudioStates();
  }

  private resetAudioStates(): void {
    this.renderRequestAudioService.abortAll();
    this.renderRequestAudioService.revokeUrls();
    this.fullAudioGenerationService.clearAudio();
    // Destroy all part wavesurfers (demo stays alive)
    Object.keys(this.renderRequestAudioPlayingStates).forEach((k) => {
      this.waveSurferService.destroy(`part:${k}`);
    });
    this.waveSurferService.destroy('full');
    this.fullPlanAudioPlaying = false;
    this.renderRequestAudioPlayingStates = {};
  }

  private playAudioPath(path: string, sampleKey: string): void {
    this.waveSurferService.pauseAll();
    this.voiceSampleService.play(path, sampleKey, () => this.scrollToSection('cast-section'));
  }

  private initializeDemoWaveform(): void {
    if (!this.demoWaveformElement || this.waveSurferService.get('demo')) return;
    const ws = this.waveSurferService.create('demo', this.demoWaveformElement.nativeElement, '/assets/audio/voice-samples/full-text-preview.mp3');
    this.waveSurferService.bind(ws, () => {
      this.voiceSampleService.pause();
      this.waveSurferService.pauseAll(ws);
    }, (playing) => { this.demoPlaying = playing; });
  }

  private initializeFullWaveform(): void {
    if (!this.fullWaveformElement || !this.fullPlanAudioUrl) return;
    this.waveSurferService.destroy('full');
    const ws = this.waveSurferService.create('full', this.fullWaveformElement.nativeElement, this.fullPlanAudioUrl);
    this.waveSurferService.bind(ws, () => {
      this.voiceSampleService.pause();
      this.waveSurferService.pauseAll(ws);
    }, (playing) => { this.fullPlanAudioPlaying = playing; });
  }

  private initializeRenderRequestWaveforms(): void {
    if (!this.renderRequestWaveformElements) return;

    this.renderRequestWaveformElements.forEach((waveformElement) => {
      const requestIndex = Number(waveformElement.nativeElement.dataset['requestIndex']);
      const audioUrl = this.renderRequestAudioService.audioStates[requestIndex]?.audioUrl;

      if (!Number.isFinite(requestIndex) || !audioUrl || this.waveSurferService.get(`part:${requestIndex}`)) return;

      const ws = this.waveSurferService.create(`part:${requestIndex}`, waveformElement.nativeElement, audioUrl);
      this.waveSurferService.bind(ws, () => {
        this.voiceSampleService.pause();
        this.waveSurferService.pauseAll(ws);
      }, (playing) => { this.renderRequestAudioPlayingStates[requestIndex] = playing; });
    });
  }

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

    this.cast.forEach((speaker) => add(speaker.speakerName));
    this.scriptTurns.forEach((turn) => add(turn.speaker));
    this.annotatedTurns.forEach((turn) => add(turn.speaker));
    return names.length > 0 ? names : this.heroCast.map((speaker) => speaker.name);
  }

  private castMemberForSpeaker(speakerName: string): SpeakerVoiceAnalysisItem | null {
    const key = normalizedSpeakerKey(speakerName);
    return this.cast.find((speaker) => normalizedSpeakerKey(speaker.speakerName) === key) ?? null;
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
