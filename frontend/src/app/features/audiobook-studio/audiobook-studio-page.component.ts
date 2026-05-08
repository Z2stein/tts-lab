import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, OnDestroy, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import WaveSurfer from 'wavesurfer.js';
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
  turns: IndexedSpeakerSplitTurn[];
}

interface IndexedSpeakerSplitTurn {
  index: number;
  turn: SpeakerSplitTurn;
}

interface AnnotatedMarkup {
  tags: string[];
  text: string;
}

interface HeroCastMember {
  name: string;
  tone: string;
  initials: string;
}

interface JourneyStep {
  icon: string;
  title: string;
  description: string;
  sectionId: string;
}

type WorkflowStepKey = 'story' | 'cast' | 'script' | 'performance' | 'audio';
type WorkflowStepStatus = 'completed' | 'current' | 'warning' | 'locked' | 'upcoming';
type RenderRequestStatus = 'not-generated' | 'generating' | 'generated' | 'failed';

interface WorkflowStep {
  key: WorkflowStepKey;
  label: string;
  sectionId: string;
  status: WorkflowStepStatus;
  statusLabel: string;
}

interface CurrentTask {
  title: string;
  body: string;
  nextAction: string;
  sectionId: string;
}

interface RenderRequestAudioState {
  status: RenderRequestStatus;
  partNumber: number;
  speaker: string | null;
  voice: string | null;
  blob: Blob | null;
  error: string | null;
  audioUrl: string | null;
  filename: string | null;
  generatedAt: Date | null;
}

interface SpeakerAccent {
  color: string;
  shadow: string;
}

export function formatSpeakerDisplayName(speakerName: string): string {
  return speakerName
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

@Component({
  selector: 'app-audiobook-studio-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './audiobook-studio-page.component.html',
  styleUrl: './audiobook-studio-page.component.css'
})
export class AudiobookStudioPageComponent implements AfterViewInit, OnDestroy {
  readonly benefitChips = ['Multi-speaker', 'Scene detection', 'Voice previews', 'Export MP3'];

  readonly heroCast: HeroCastMember[] = [
    { name: 'Narrator', tone: 'warm calm', initials: 'N' },
    { name: 'Mara', tone: 'young tense', initials: 'M' },
    { name: 'Jonas', tone: 'soft nervous', initials: 'J' },
    { name: 'Station Keeper', tone: 'old gravelly', initials: 'SK' }
  ];

  readonly journeySteps: JourneyStep[] = [
    {
      icon: '01',
      title: 'Paste your story',
      description: 'Drop in a chapter, scene, or script and keep the original story flow intact.',
      sectionId: 'story-section'
    },
    {
      icon: '02',
      title: 'Discover the cast',
      description: 'AI identifies the narrator and characters, then suggests fitting voice directions.',
      sectionId: 'cast-section'
    },
    {
      icon: '03',
      title: 'Direct the performance',
      description: 'Review dialogue, approve pacing, and add emotional notes before production.',
      sectionId: 'script-section'
    },
    {
      icon: '04',
      title: 'Generate audio',
      description: 'Create a multi-speaker MP3 from the final production plan.',
      sectionId: 'audio-section'
    }
  ];

  readonly speakerAccents: SpeakerAccent[] = [
    { color: '#f0ad5d', shadow: 'rgba(240, 173, 93, 0.34)' },
    { color: '#c965ff', shadow: 'rgba(201, 101, 255, 0.34)' },
    { color: '#48b5ff', shadow: 'rgba(72, 181, 255, 0.32)' },
    { color: '#8fe77a', shadow: 'rgba(143, 231, 122, 0.3)' },
    { color: '#ff7da8', shadow: 'rgba(255, 125, 168, 0.3)' },
    { color: '#7de7d4', shadow: 'rgba(125, 231, 212, 0.3)' }
  ];

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
  private activeSampleAudio: HTMLAudioElement | null = null;
  private demoWaveformElement: ElementRef<HTMLElement> | null = null;
  private fullWaveformElement: ElementRef<HTMLElement> | null = null;
  private demoWaveSurfer: WaveSurfer | null = null;
  private fullWaveSurfer: WaveSurfer | null = null;
  private renderRequestWaveSurfers = new Map<number, WaveSurfer>();

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

    this.fullPlanAudioLoading = true;
    this.fullPlanAudioError = null;

    try {
      for (const [requestIndex, renderRequest] of this.renderRequests.entries()) {
        const state = this.renderRequestAudioState(requestIndex);
        if (state.status === 'generated' && state.blob) {
          continue;
        }
        await this.generateAudioForRenderRequest(renderRequest, requestIndex, { keepFullLoading: true });
      }

      const unavailableParts = this.renderRequests
        .map((_, requestIndex) => this.renderRequestAudioState(requestIndex))
        .filter((state) => state.status !== 'generated' || !state.blob);

      if (unavailableParts.length > 0) {
        this.clearFullPlanAudio();
        this.fullPlanAudioError = 'Some parts could not be generated. Retry failed parts before creating the full audiobook.';
        return;
      }

      const audioParts = this.renderRequests.map((_, requestIndex) => this.renderRequestAudioState(requestIndex).blob as Blob);
      this.setFullPlanAudio(new Blob(audioParts, { type: 'audio/mpeg' }), 'audiobook-preview.mp3');
    } catch (error) {
      this.fullPlanAudioError = 'One audio part could not be generated. The other parts are still available. You can retry this part or edit the text.';
    } finally {
      this.fullPlanAudioLoading = false;
    }
  }

  async generateAudioForRenderRequest(
    renderRequest: SingleSpeakerRenderRequest,
    requestIndex: number,
    options: { keepFullLoading?: boolean } = {}
  ): Promise<void> {
    const state = this.renderRequestAudioState(requestIndex);
    state.status = 'generating';
    state.error = null;
    this.clearFullPlanAudio();

    try {
      const download = await this.ttsWorkbenchService.createAudioForRenderRequest(renderRequest);
      this.setRenderRequestAudio(requestIndex, download.blob, `tts-audio-part-${requestIndex + 1}.mp3`);
    } catch (error) {
      if (state.blob && state.audioUrl) {
        state.status = 'generated';
        state.error = 'This part could not be regenerated. The previous audio is still available.';
      } else {
        state.status = 'failed';
        state.error = 'One audio part could not be generated. The other parts are still available. You can retry this part or edit the text.';
      }
    } finally {
      if (!options.keepFullLoading) {
        this.fullPlanAudioLoading = false;
      }
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
        generatedAt: null
      };
    }
    return this.renderRequestAudioStates[requestIndex];
  }

  anyAudioLoading(): boolean {
    return this.fullPlanAudioLoading || Object.values(this.renderRequestAudioStates).some((state) => state.status === 'generating');
  }

  generatedPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'generated').length;
  }

  failedPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'failed').length;
  }

  missingPartCount(): number {
    return this.renderRequests.filter((_, requestIndex) => this.renderRequestAudioState(requestIndex).status === 'not-generated').length;
  }

  partsToGenerateCount(): number {
    return this.missingPartCount() + this.failedPartCount();
  }

  audiobookReadinessSummary(): string {
    const ready = this.generatedPartCount();
    const total = this.renderRequests.length;
    const missing = this.missingPartCount();
    const failed = this.failedPartCount();

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

    return `${ready} of ${total} parts ready - ${details.join(', ')}`;
  }

  audiobookGenerationExplanation(): string {
    const remaining = this.partsToGenerateCount();
    if (remaining === 0) {
      return 'All parts are ready. Generate audiobook preview will merge them into the final preview.';
    }

    return `Generate audiobook preview will use the parts that are already ready and only generate the ${remaining} missing or failed ${remaining === 1 ? 'part' : 'parts'} before merging.`;
  }

  partStatusLabel(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    switch (state.status) {
      case 'generating':
        return 'Generating...';
      case 'generated':
        return 'Ready to listen';
      case 'failed':
        return 'Failed - retry this part';
      default:
        return 'Not generated yet';
    }
  }

  partActionLabel(requestIndex: number): string {
    const state = this.renderRequestAudioState(requestIndex);
    if (state.status === 'generating') {
      return 'Generating...';
    }
    if (state.status === 'failed') {
      return 'Retry this part';
    }
    return 'Generate this part';
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

  fullPlanDurationLabel(): string {
    return this.durationLabelFor(this.fullWaveSurfer);
  }

  renderRequestDurationLabel(requestIndex: number): string {
    return this.durationLabelFor(this.renderRequestWaveSurfers.get(requestIndex) ?? null);
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
    return this.displaySpeakerName(speakerName)
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
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
    this.cancelCastEdit();
    this.cancelScriptTurnEdit();
    this.resetAudioStates();
  }

  private resetAudioStates(): void {
    this.fullPlanAudioLoading = false;
    this.fullPlanAudioError = null;
    this.fullPlanAudioPlaying = false;
    this.renderRequestAudioPlayingStates = {};
    this.fullWaveSurfer?.destroy();
    this.fullWaveSurfer = null;
    this.renderRequestWaveSurfers.forEach((waveSurfer) => waveSurfer.destroy());
    this.renderRequestWaveSurfers.clear();
    this.revokeGeneratedAudioUrls();
  }

  private setFullPlanAudio(blob: Blob, filename: string): void {
    if (this.fullPlanAudioUrl) {
      window.URL.revokeObjectURL(this.fullPlanAudioUrl);
    }
    this.fullPlanAudioUrl = window.URL.createObjectURL(blob);
    this.fullPlanAudioFilename = filename;
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
    state.status = 'generated';
    state.blob = blob;
    state.audioUrl = window.URL.createObjectURL(blob);
    state.filename = filename;
    state.error = null;
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
    const key = this.normalizedSpeakerKey(speakerName ?? '');
    const names = this.knownSpeakerNames();
    const index = Math.max(0, names.findIndex((name) => this.normalizedSpeakerKey(name) === key));
    return this.speakerAccents[index % this.speakerAccents.length];
  }

  private knownSpeakerNames(): string[] {
    const names: string[] = [];
    const add = (speakerName: string | null | undefined) => {
      if (!speakerName) {
        return;
      }
      const displayName = this.displaySpeakerName(speakerName);
      if (!names.some((name) => this.normalizedSpeakerKey(name) === this.normalizedSpeakerKey(displayName))) {
        names.push(displayName);
      }
    };

    this.cast.forEach((speaker) => add(speaker.speakerName));
    this.scriptTurns.forEach((turn) => add(turn.speaker));
    this.annotatedTurns.forEach((turn) => add(turn.speaker));
    return names.length > 0 ? names : this.heroCast.map((speaker) => speaker.name);
  }

  private castMemberForSpeaker(speakerName: string): SpeakerVoiceAnalysisItem | null {
    const key = this.normalizedSpeakerKey(speakerName);
    return this.cast.find((speaker) => this.normalizedSpeakerKey(speaker.speakerName) === key) ?? null;
  }

  private castSpeakerForVoice(voiceName: string): string | null {
    const key = this.normalizedSpeakerKey(voiceName);
    const castMember = this.cast.find((speaker) => this.normalizedSpeakerKey(speaker.voiceSuggestion) === key);
    return castMember?.speakerName ?? null;
  }

  private normalizedSpeakerKey(value: string): string {
    return this.displaySpeakerName(value).toLowerCase();
  }

  private durationLabelFor(waveSurfer: WaveSurfer | null): string {
    if (!waveSurfer) {
      return '00:00';
    }

    const duration = waveSurfer.getDuration();
    if (!Number.isFinite(duration) || duration <= 0) {
      return '00:00';
    }

    const minutes = Math.floor(duration / 60).toString().padStart(2, '0');
    const seconds = Math.floor(duration % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
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
