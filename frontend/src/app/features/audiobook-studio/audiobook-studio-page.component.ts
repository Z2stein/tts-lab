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
  RequestCancelReason,
  RenderRequestStatus,
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
  templateUrl: './audiobook-studio-page.component.html',
  styleUrl: './audiobook-studio-page.component.css'
})
export class AudiobookStudioPageComponent implements AfterViewInit, OnDestroy {
  partGenerationTimeoutMs = 120_000;

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
  fullPlanAudioLoading = false;
  fullPlanAudioError: string | null = null;
  fullPlanAudioStale = false;
  fullPlanAudioStatusMessage: string | null = null;
  fullPlanAudioUrl: string | null = null;
  fullPlanAudioFilename: string | null = null;
  renderRequestAudioStates: Record<number, RenderRequestAudioState> = {};
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
  activeSampleKey: string | null = null;
  generationClockTick = 0;

  private activeSampleAudio: HTMLAudioElement | null = null;
  private demoWaveformElement: ElementRef<HTMLElement> | null = null;
  private fullWaveformElement: ElementRef<HTMLElement> | null = null;
  private demoWaveSurfer: WaveSurfer | null = null;
  private fullWaveSurfer: WaveSurfer | null = null;
  private renderRequestWaveSurfers = new Map<number, WaveSurfer>();
  private renderRequestGenerationCounter = 0;
  private fullAudioGenerationRunId = 0;
  private fullAudioGenerationCanceled = false;
  private fullAudioGenerationActiveRequestIndex: number | null = null;
  private generationClockHandle: number | null = null;

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

  constructor(private readonly ttsWorkbenchService: TtsWorkbenchService) {}

  get wordCount(): number {
    return this.storyTextControl.value.trim().split(/\s+/).filter(Boolean).length;
  }

  get characterCount(): number {
    return this.storyTextControl.value.length;
  }

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
    if (this.demoWaveSurfer) {
      void this.demoWaveSurfer.playPause();
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
    if (!this.audioProductionPlan || this.renderRequests.length === 0) {
      return;
    }

    if (this.fullPlanAudioLoading || this.anyAudioLoading()) {
      return;
    }

    const runId = ++this.fullAudioGenerationRunId;
    this.fullAudioGenerationCanceled = false;
    this.fullAudioGenerationActiveRequestIndex = null;
    this.fullPlanAudioLoading = true;
    this.fullPlanAudioError = null;
    this.fullPlanAudioStale = this.fullPlanAudioUrl !== null;
    this.fullPlanAudioStatusMessage = this.fullPlanAudioUrl
      ? 'Rebuilding the audiobook preview. Existing audio stays available until the new preview is ready.'
      : 'Building the audiobook preview from the generated parts.';
    this.startGenerationClock();

    try {
      for (const [requestIndex, renderRequest] of this.renderRequests.entries()) {
        if (runId !== this.fullAudioGenerationRunId || this.fullAudioGenerationCanceled) {
          break;
        }

        const state = this.renderRequestAudioState(requestIndex);
        if (state.status === 'generated' && state.blob) {
          continue;
        }
        this.fullAudioGenerationActiveRequestIndex = requestIndex;
        await this.generateAudioForRenderRequest(renderRequest, requestIndex, { fullRunId: runId });
      }

      if (runId !== this.fullAudioGenerationRunId) {
        return;
      }

      if (this.fullAudioGenerationCanceled) {
        this.fullPlanAudioLoading = false;
        this.fullPlanAudioStatusMessage = 'Generation canceled. You can retry the pending part.';
        return;
      }

      const incompleteParts = this.renderRequests
        .map((_, requestIndex) => this.renderRequestAudioState(requestIndex))
        .filter((state) => state.status !== 'generated' || !state.blob);

      if (incompleteParts.length > 0) {
        this.fullPlanAudioLoading = false;
        this.fullPlanAudioStatusMessage = this.audiobookReadinessSummary();
        return;
      }

      const audioParts = this.renderRequests.map((_, requestIndex) => this.renderRequestAudioState(requestIndex).blob as Blob);
      this.setFullPlanAudio(new Blob(audioParts, { type: 'audio/mpeg' }), 'audiobook-preview.mp3');
      this.fullPlanAudioStatusMessage = 'Audiobook preview is ready.';
    } catch (error) {
      if (!this.fullAudioGenerationCanceled) {
        this.fullPlanAudioError = 'One audio part could not be generated. The other parts are still available. You can retry this part or edit the text.';
      }
    } finally {
      if (runId === this.fullAudioGenerationRunId) {
        this.fullPlanAudioLoading = false;
        this.fullAudioGenerationActiveRequestIndex = null;
        this.stopGenerationClockIfIdle();
      }
    }
  }

  async generateAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    options: { fullRunId?: number } = {}
  ): Promise<void> {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.status === 'generating' && state.inFlightPromise) {
      return state.inFlightPromise;
    }

    const requestId = ++this.renderRequestGenerationCounter;
    const controller = new AbortController();
    const timeoutHandle = window.setTimeout(() => {
      const activeState = this.renderRequestAudioState(requestIndex);
      if (activeState.requestId !== requestId || activeState.status !== 'generating') {
        return;
      }
      activeState.cancelReason = 'timeout';
      activeState.controller?.abort();
    }, this.partGenerationTimeoutMs);

    state.status = 'generating';
    state.error = null;
    state.startedAt = Date.now();
    state.requestId = requestId;
    state.timeoutHandle = timeoutHandle;
    state.controller = controller;
    state.cancelReason = null;
    state.inFlightPromise = this.runRenderRequestGeneration(renderRequest, requestIndex, requestId, controller, options.fullRunId);
    this.startGenerationClock();
    return state.inFlightPromise;
  }

  cancelRenderRequestGeneration(requestIndex: number): void {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.status !== 'generating') {
      return;
    }

    state.cancelReason = 'cancel';
    state.controller?.abort();
  }

  cancelFullAudioGeneration(): void {
    if (!this.fullPlanAudioLoading) {
      return;
    }

    this.fullAudioGenerationCanceled = true;
    const activeIndex = this.fullAudioGenerationActiveRequestIndex;
    if (activeIndex !== null) {
      this.cancelRenderRequestGeneration(activeIndex);
    }
    this.fullPlanAudioLoading = false;
    this.fullPlanAudioStatusMessage = 'Generation canceled. You can retry the pending part.';
    this.stopGenerationClockIfIdle();
  }

  markupFor(turn: AnnotatedSpeakerTurn): AnnotatedMarkup {
    return markupFor(turn);
  }

  renderRequestAudioState(requestIndex: number): RenderRequestAudioState {
    if (!this.renderRequestAudioStates[requestIndex]) {
      const renderRequest = this.renderRequests[requestIndex];
      this.renderRequestAudioStates[requestIndex] = {
        status: 'not-generated',
        partNumber: requestIndex + 1,
        speaker: this.renderRequestSpeaker(renderRequest),
        voice: this.renderRequestVoice(renderRequest),
        blob: null,
        error: null,
        audioUrl: null,
        filename: null,
        generatedAt: null,
        startedAt: null,
        requestId: 0,
        timeoutHandle: null,
        controller: null,
        cancelReason: null,
        inFlightPromise: null
      };
    }
    return this.renderRequestAudioStates[requestIndex];
  }

  anyAudioLoading(): boolean {
    return this.fullPlanAudioLoading || Object.values(this.renderRequestAudioStates).some((state) => state.status === 'generating');
  }

  generatedPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => {
      const state = this.renderRequestAudioState(requestIndex);
      return state.status === 'generated' || (state.blob !== null && state.status !== 'generating');
    }).length;
  }

  failedPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'failed').length;
  }

  canceledPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'canceled').length;
  }

  timedOutPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'timed-out').length;
  }

  missingPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'not-generated').length;
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
    if (missing > 0) {
      details.push(`${missing} missing`);
    }
    if (failed > 0) {
      details.push(`${failed} failed`);
    }
    if (canceled > 0) {
      details.push(`${canceled} canceled`);
    }
    if (timedOut > 0) {
      details.push(`${timedOut} timed out`);
    }

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
    if (state.status === 'generating') {
      return 'Cancel';
    }
    if (state.status === 'failed' || state.status === 'canceled' || state.status === 'timed-out') {
      return 'Retry this part';
    }
    if (state.status === 'generated') {
      return 'Regenerate part';
    }
    return 'Generate this part';
  }

  partElapsedLabel(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.status !== 'generating' || state.startedAt === null) {
      return '';
    }

    return formatElapsedTime(Date.now() - state.startedAt);
  }

  currentGenerationStatusLabel(): string {
    const currentIndex = this.currentGeneratingRequestIndex();
    if (currentIndex !== null) {
      const readyCount = this.generatedPartCount();
      return `Generating part ${currentIndex + 1} of ${this.renderRequests.length} - ${readyCount} parts already ready`;
    }

    if (this.fullPlanAudioLoading) {
      return 'Generating audiobook preview...';
    }

    if (this.fullPlanAudioError) {
      return this.fullPlanAudioError;
    }

    if (this.fullPlanAudioStatusMessage) {
      return this.fullPlanAudioStatusMessage;
    }

    if (this.fullPlanAudioUrl && this.fullPlanAudioStale) {
      return 'Audiobook preview needs regeneration after part updates.';
    }

    if (this.fullPlanAudioUrl) {
      return 'Audiobook preview ready.';
    }

    return this.audiobookReadinessSummary();
  }

  currentGenerationDetails(): string {
    if (this.currentGeneratingRequestIndex() !== null) {
      return 'You can cancel the active part generation. Completed parts stay available, and canceled, timed-out, or failed parts can be retried.';
    }

    if (this.fullPlanAudioLoading) {
      return this.audiobookGenerationExplanation();
    }

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
    const activeIndex = Object.entries(this.renderRequestAudioStates).find(([, state]) => state.status === 'generating');
    return activeIndex ? Number(activeIndex[0]) : null;
  }

  fullPlanDurationLabel(): string {
    return durationLabelFor(this.fullWaveSurfer);
  }

  renderRequestDurationLabel(requestIndex: number): string {
    return durationLabelFor(this.renderRequestWaveSurfers.get(requestIndex) ?? null);
  }

  toggleFullGeneratedAudio(): void {
    void this.fullWaveSurfer?.playPause();
  }

  toggleRenderRequestAudio(requestIndex: number): void {
    void this.renderRequestWaveSurfers.get(requestIndex)?.playPause();
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
    this.playAudioPath(this.voiceSamplePathFor(speakerName), `voice:${this.displaySpeakerName(speakerName)}`);
  }

  isVoiceSamplePlaying(speakerName: string): boolean {
    return this.activeSampleKey === `voice:${this.displaySpeakerName(speakerName)}`;
  }

  voiceSamplePathFor(speakerName: string): string {
    const normalizedName = this.displaySpeakerName(speakerName).toLowerCase();
    if (normalizedName.includes('mara')) {
      return '/assets/audio/voice-samples/mara.mp3';
    }
    if (normalizedName.includes('jonas')) {
      return '/assets/audio/voice-samples/jonas.mp3';
    }
    if (normalizedName.includes('station keeper')) {
      return '/assets/audio/voice-samples/station-keeper.mp3';
    }
    return '/assets/audio/voice-samples/narrator.mp3';
  }

  startCastEdit(index: number): void {
    this.editingCastIndex = index;
    this.castEditDraft = { ...this.cast[index] };
  }

  saveCastEdit(index: number): void {
    if (!this.castEditDraft) {
      return;
    }

    const draft: SpeakerVoiceAnalysisItem = { ...this.castEditDraft };
    this.cast = this.cast.map((speaker, speakerIndex) =>
      speakerIndex === index ? draft : speaker
    );
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
    if (!this.scriptTurnEditDraft) {
      return;
    }

    const draft: SpeakerSplitTurn = { ...this.scriptTurnEditDraft };
    this.scriptTurns = this.scriptTurns.map((turn, turnIndex) =>
      turnIndex === index ? draft : turn
    );
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
    this.fullAudioGenerationCanceled = true;
    this.abortAllRenderRequestGenerations();
    if (this.generationClockHandle !== null) {
      window.clearInterval(this.generationClockHandle);
      this.generationClockHandle = null;
    }
    this.activeSampleAudio?.pause();
    this.demoWaveSurfer?.destroy();
    this.fullWaveSurfer?.destroy();
    this.renderRequestWaveSurfers.forEach((waveSurfer) => waveSurfer.destroy());
    this.revokeGeneratedAudioUrls();
  }

  ngAfterViewInit(): void {
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
    this.fullPlanAudioStale = false;
    this.fullPlanAudioStatusMessage = null;
    this.cancelCastEdit();
    this.cancelScriptTurnEdit();
    this.resetAudioStates();
  }

  private resetAudioStates(): void {
    this.abortAllRenderRequestGenerations();
    this.fullPlanAudioLoading = false;
    this.fullPlanAudioError = null;
    this.fullPlanAudioStale = false;
    this.fullPlanAudioStatusMessage = null;
    this.fullPlanAudioPlaying = false;
    this.renderRequestAudioPlayingStates = {};
    this.fullWaveSurfer?.destroy();
    this.fullWaveSurfer = null;
    this.renderRequestWaveSurfers.forEach((waveSurfer) => waveSurfer.destroy());
    this.renderRequestWaveSurfers.clear();
    this.fullAudioGenerationCanceled = false;
    this.fullAudioGenerationActiveRequestIndex = null;
    if (this.generationClockHandle !== null) {
      window.clearInterval(this.generationClockHandle);
      this.generationClockHandle = null;
    }
    this.revokeGeneratedAudioUrls();
  }

  private setFullPlanAudio(blob: Blob, filename: string): void {
    if (this.fullPlanAudioUrl) {
      window.URL.revokeObjectURL(this.fullPlanAudioUrl);
    }
    this.fullPlanAudioUrl = window.URL.createObjectURL(blob);
    this.fullPlanAudioFilename = filename;
    this.fullPlanAudioStale = false;
    window.setTimeout(() => this.initializeFullWaveform());
  }

  private setRenderRequestAudio(requestIndex: number, blob: Blob, filename: string): void {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.audioUrl) {
      window.URL.revokeObjectURL(state.audioUrl);
    }
    this.renderRequestWaveSurfers.get(requestIndex)?.destroy();
    this.renderRequestWaveSurfers.delete(requestIndex);
    this.renderRequestAudioPlayingStates[requestIndex] = false;
    state.blob = blob;
    state.audioUrl = window.URL.createObjectURL(blob);
    state.filename = filename;
    state.generatedAt = new Date();
    window.setTimeout(() => this.initializeRenderRequestWaveforms());
  }

  private clearFullPlanAudio(): void {
    if (this.fullPlanAudioUrl) {
      window.URL.revokeObjectURL(this.fullPlanAudioUrl);
    }
    this.fullWaveSurfer?.destroy();
    this.fullWaveSurfer = null;
    this.fullPlanAudioUrl = null;
    this.fullPlanAudioFilename = null;
    this.fullPlanAudioPlaying = false;
  }

  private downloadBlobUrl(url: string, filename: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  private revokeGeneratedAudioUrls(): void {
    this.clearFullPlanAudio();
    Object.values(this.renderRequestAudioStates).forEach((state) => {
      if (state.audioUrl) {
        window.URL.revokeObjectURL(state.audioUrl);
      }
    });
    this.renderRequestAudioStates = {};
  }

  private clearRenderRequestGeneration(requestIndex: number, requestId: number): void {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.requestId !== requestId) {
      return;
    }

    if (state.timeoutHandle !== null) {
      window.clearTimeout(state.timeoutHandle);
    }

    state.controller = null;
    state.timeoutHandle = null;
    state.startedAt = null;
    state.inFlightPromise = null;
  }

  private abortAllRenderRequestGenerations(): void {
    Object.values(this.renderRequestAudioStates).forEach((state) => {
      state.controller?.abort();
      if (state.timeoutHandle !== null) {
        window.clearTimeout(state.timeoutHandle);
      }
      state.controller = null;
      state.timeoutHandle = null;
      state.startedAt = null;
      state.inFlightPromise = null;
    });
    this.fullAudioGenerationActiveRequestIndex = null;
  }

  private async runRenderRequestGeneration(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    requestId: number,
    controller: AbortController,
    fullRunId?: number
  ): Promise<void> {
    const state = this.renderRequestAudioState(requestIndex);

    try {
      const download = await this.ttsWorkbenchService.createAudioForRenderRequest(renderRequest, { signal: controller.signal });
      if (!this.isCurrentRenderRequestGeneration(requestIndex, requestId)) {
        return;
      }

      this.setRenderRequestAudio(requestIndex, download.blob, `tts-audio-part-${requestIndex + 1}.mp3`);
      state.status = 'generated';
      state.error = null;
      state.cancelReason = null;
      if (this.fullPlanAudioUrl) {
        this.fullPlanAudioStale = true;
        this.fullPlanAudioStatusMessage = 'Audiobook preview needs regeneration because one or more parts changed.';
      } else {
        this.fullPlanAudioStatusMessage = null;
      }
    } catch (error) {
      if (!this.isCurrentRenderRequestGeneration(requestIndex, requestId)) {
        return;
      }

      if (this.isAbortError(error)) {
        if (state.cancelReason === 'timeout') {
          state.status = 'timed-out';
          state.error = 'This part took too long and was stopped. Try again or edit the text.';
        } else {
          state.status = 'canceled';
          state.error = 'Generation canceled. You can retry this part.';
        }
      } else {
        state.status = 'failed';
        state.error = 'One part failed. Other generated parts are still available.';
      }
    } finally {
      if (this.isCurrentRenderRequestGeneration(requestIndex, requestId)) {
        this.clearRenderRequestGeneration(requestIndex, requestId);
        if (fullRunId !== undefined && this.fullAudioGenerationRunId === fullRunId && this.fullAudioGenerationActiveRequestIndex === requestIndex) {
          this.fullAudioGenerationActiveRequestIndex = null;
        }
        this.stopGenerationClockIfIdle();
      }
    }
  }

  private isCurrentRenderRequestGeneration(requestIndex: number, requestId: number): boolean {
    return this.renderRequestAudioState(requestIndex).requestId === requestId;
  }

  private isAbortError(error: unknown): boolean {
    return error instanceof DOMException && error.name === 'AbortError';
  }

  private startGenerationClock(): void {
    if (this.generationClockHandle !== null) {
      return;
    }

    this.generationClockHandle = window.setInterval(() => {
      this.generationClockTick += 1;
    }, 1000);
  }

  private stopGenerationClockIfIdle(): void {
    if (!this.anyAudioLoading() && this.generationClockHandle !== null) {
      window.clearInterval(this.generationClockHandle);
      this.generationClockHandle = null;
    }
  }

  private renderRequestSpeaker(renderRequest: SingleSpeakerRenderRequest | undefined): string | null {
    const voice = this.objectRecord(renderRequest?.voice);
    const speaker = voice?.['speakerName'] ?? voice?.['speaker'] ?? voice?.['name'];
    if (typeof speaker === 'string' && speaker.trim().length > 0) {
      return this.castSpeakerForVoice(speaker) ?? speaker;
    }
    return null;
  }

  private renderRequestVoice(renderRequest: SingleSpeakerRenderRequest | undefined): string | null {
    const voice = this.objectRecord(renderRequest?.voice);
    const voiceName = voice?.['name'] ?? voice?.['voiceName'];
    return typeof voiceName === 'string' && voiceName.trim().length > 0 ? voiceName : null;
  }

  private objectRecord(value: unknown): Record<string, unknown> | null {
    return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null;
  }

  private playAudioPath(path: string, sampleKey: string): void {
    if (this.activeSampleKey === sampleKey && this.activeSampleAudio && !this.activeSampleAudio.paused) {
      this.activeSampleAudio.pause();
      this.activeSampleKey = null;
      return;
    }

    this.pauseWaveSurfers();
    this.activeSampleAudio?.pause();
    this.activeSampleAudio = new Audio(path);
    this.activeSampleKey = sampleKey;
    this.activeSampleAudio.addEventListener('pause', () => {
      if (this.activeSampleAudio?.paused) {
        this.activeSampleKey = null;
      }
    });
    this.activeSampleAudio.addEventListener('ended', () => {
      this.activeSampleKey = null;
    });
    this.activeSampleAudio.play().catch(() => {
      this.activeSampleKey = null;
      this.scrollToSection('cast-section');
    });
  }

  private initializeDemoWaveform(): void {
    if (!this.demoWaveformElement || this.demoWaveSurfer) {
      return;
    }

    this.demoWaveSurfer = this.createWaveSurfer(this.demoWaveformElement.nativeElement, '/assets/audio/voice-samples/full-text-preview.mp3');
    this.bindPlaybackState(this.demoWaveSurfer, (playing) => {
      this.demoPlaying = playing;
    }, this.demoWaveSurfer);
  }

  private initializeFullWaveform(): void {
    if (!this.fullWaveformElement || !this.fullPlanAudioUrl) {
      return;
    }

    this.fullWaveSurfer?.destroy();
    this.fullWaveSurfer = this.createWaveSurfer(this.fullWaveformElement.nativeElement, this.fullPlanAudioUrl);
    this.bindPlaybackState(this.fullWaveSurfer, (playing) => {
      this.fullPlanAudioPlaying = playing;
    }, this.fullWaveSurfer);
  }

  private initializeRenderRequestWaveforms(): void {
    if (!this.renderRequestWaveformElements) {
      return;
    }

    this.renderRequestWaveformElements.forEach((waveformElement) => {
      const requestIndex = Number(waveformElement.nativeElement.dataset['requestIndex']);
      const audioUrl = this.renderRequestAudioState(requestIndex).audioUrl;

      if (!Number.isFinite(requestIndex) || !audioUrl || this.renderRequestWaveSurfers.has(requestIndex)) {
        return;
      }

      const waveSurfer = this.createWaveSurfer(waveformElement.nativeElement, audioUrl);
      this.renderRequestWaveSurfers.set(requestIndex, waveSurfer);
      this.bindPlaybackState(waveSurfer, (playing) => {
        this.renderRequestAudioPlayingStates[requestIndex] = playing;
      }, waveSurfer);
    });
  }

  private createWaveSurfer(container: HTMLElement, url: string): WaveSurfer {
    container.innerHTML = '';
    return WaveSurfer.create({
      container,
      url,
      height: 58,
      waveColor: '#596174',
      progressColor: '#f0ad5d',
      cursorColor: '#ffd591',
      cursorWidth: 2,
      barWidth: 3,
      barGap: 3,
      barRadius: 3,
      normalize: true,
      dragToSeek: true
    });
  }

  private bindPlaybackState(waveSurfer: WaveSurfer, update: (playing: boolean) => void, current: WaveSurfer): void {
    waveSurfer.on('play', () => {
      this.activeSampleAudio?.pause();
      this.activeSampleKey = null;
      this.pauseWaveSurfers(current);
      update(true);
    });
    waveSurfer.on('pause', () => update(false));
    waveSurfer.on('finish', () => update(false));
  }

  private pauseWaveSurfers(except: WaveSurfer | null = null): void {
    for (const waveSurfer of [this.demoWaveSurfer, this.fullWaveSurfer, ...this.renderRequestWaveSurfers.values()]) {
      if (waveSurfer && waveSurfer !== except && waveSurfer.isPlaying()) {
        waveSurfer.pause();
      }
    }
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
      if (!speakerName) {
        return;
      }
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

  private castSpeakerForVoice(voiceName: string): string | null {
    const key = normalizedSpeakerKey(voiceName);
    const castMember = this.cast.find((speaker) => normalizedSpeakerKey(speaker.voiceSuggestion) === key);
    return castMember?.speakerName ?? null;
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
