import { ComponentFixture, fakeAsync, TestBed, tick, flushMicrotasks, discardPeriodicTasks, flush } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AudiobookStudioWorkspaceComponent, formatSpeakerDisplayName } from './audiobook-studio-page.component';
import { PerformanceNotesComponent } from './components/performance-notes/performance-notes.component';
import { AudiobookApiService } from '../audiobook-shared/service/audiobook-api.service';
import { AudiobookWorkflowService } from '../audiobook-shared/service/audiobook-workflow.service';
import { AudiobookLibraryService } from '../audiobook-library/services/audiobook-library.service';
import { VoicePickerService } from './services/voice-picker.service';

describe('AudiobookStudioWorkspaceComponent', () => {
  let fixture: ComponentFixture<AudiobookStudioWorkspaceComponent>;
  let component: AudiobookStudioWorkspaceComponent;
  let audiobookWorkflowService: jasmine.SpyObj<AudiobookWorkflowService>;
  let audiobookApiService: jasmine.SpyObj<AudiobookApiService>;
  let audiobookLibraryService: jasmine.SpyObj<AudiobookLibraryService>;
  let voicePickerService: jasmine.SpyObj<VoicePickerService>;

  beforeEach(async () => {
    audiobookWorkflowService = jasmine.createSpyObj<AudiobookWorkflowService>('AudiobookWorkflowService', [
      'analyzeSpeakers',
      'approveCast',
      'saveCast',
      'splitDialogue',
      'saveScriptPreview',
      'annotateEmotions',
      'saveProductionSettings',
      'approveScript',
      'generateFinalJson',
      'planSingleSpeakerRenderRequests',
      'createAudio',
      'markAudioGenerated'
    ]);
    audiobookApiService = jasmine.createSpyObj<AudiobookApiService>('AudiobookApiService', [
      'createAudio',
      'createAudioForRenderRequest',
      'post',
      'postJsonResponse',
      'postBlobResponse'
    ]);
    audiobookLibraryService = jasmine.createSpyObj<AudiobookLibraryService>('AudiobookLibraryService', ['updateTitle']);
    voicePickerService = jasmine.createSpyObj<VoicePickerService>('VoicePickerService', ['getVoiceCatalog']);
    voicePickerService.getVoiceCatalog.and.resolveTo([
      { id: 'zephyr', providerVoiceName: 'Zephyr', displayName: 'Zephyr', description: 'Bright.', imageUrl: '/assets/voices/zephyr/avatar.png', demoMp3Url: '/assets/voices/zephyr/demo.mp3', gender: 'MALE' },
      { id: 'puck', providerVoiceName: 'Puck', displayName: 'Puck', description: 'Playful.', imageUrl: '/assets/voices/puck/avatar.png', demoMp3Url: '/assets/voices/puck/demo.mp3', gender: 'MALE' },
    ]);
    audiobookApiService.createAudioForRenderRequest.and.callFake(async () => ({
      blob: new Blob(['generated'], { type: 'audio/mpeg' }),
      filename: 'tts-render-request-1.mp3'
    }));
    audiobookWorkflowService.saveScriptPreview.and.callFake(async (_projectId, turns) => turns);
    audiobookWorkflowService.saveCast.and.callFake(async (_projectId, payload: any) => ({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      storyText: 'Mara: We go now.',
      workflowStage: 'CAST_REVIEW',
      speakers: payload.speakers,
      scriptTurns: [],
      annotatedTurns: [],
      audioAssets: [],
      audioAssetsCurrent: false,
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    }));
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [],
      projectId: null,
      projectTitle: '',
      sourceLanguageCode: 'en-US',
      productionLanguageCode: 'en-US'
    });
    audiobookWorkflowService.markAudioGenerated.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'Mara: We go now.',
      workflowStage: 'AUDIO_GENERATED',
      speakers: [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }],
      scriptTurns: [{ speaker: 'Mara', text: 'We go now.' }],
      annotatedTurns: [{ speaker: 'Mara', text: '[urgent] We go now.' }],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);
    audiobookLibraryService.updateTitle.and.resolveTo({
      id: 'project-1',
      title: 'The Hidden Signal',
      status: 'NEEDS_REVIEW',
      speechSegmentCount: 0,
      speakerCount: null,
      totalDurationSeconds: null,
      createdAt: '2026-05-12T10:00:00Z',
      updatedAt: '2026-05-12T10:01:00Z',
      speechSegments: [],
      audioAssets: []
    } as never);

    await TestBed.configureTestingModule({
      imports: [AudiobookStudioWorkspaceComponent],
      providers: [
        { provide: AudiobookWorkflowService, useValue: audiobookWorkflowService },
        { provide: AudiobookApiService, useValue: audiobookApiService },
        { provide: AudiobookLibraryService, useValue: audiobookLibraryService },
        { provide: VoicePickerService, useValue: voicePickerService },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookStudioWorkspaceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    // These specs exercise the guided workflow; Autopilot is now the default
    // view, so switch into guided mode first (the default is covered by
    // autopilot-workspace.spec.ts).
    component.setStudioMode('guided');
    fixture.detectChanges();
  });

  it('fills the textarea when the sample story is selected', () => {
    clickButton('Paste story');
    fixture.detectChanges();
    clickButton('Use sample story');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('#story-text') as HTMLTextAreaElement;
    expect(textarea.value).toContain('Mara');
    expect(textarea.value).toContain('Jonas');
    expect(textarea.value).toContain('Station Keeper');
  });

  it('opens the Autopilot generation panel from the hero CTA', fakeAsync(() => {
    const scrollService = (component as any).scrollService as { scrollTo: jasmine.Spy; focusById: jasmine.Spy };
    spyOn(scrollService, 'scrollTo');
    spyOn(scrollService, 'focusById');

    component.focusStoryInput(new Event('click'));
    fixture.detectChanges();
    tick(50);

    expect(component.studioMode()).toBe('autopilot');
    expect(fixture.nativeElement.querySelector('[data-testid="autopilot-setup"]')).not.toBeNull();
    expect(scrollService.scrollTo).toHaveBeenCalledWith('autopilot-setup');
    expect(scrollService.focusById).toHaveBeenCalledWith('autopilot-story-text', { preventScroll: true });
  }));

  it('shows cast cards after story analysis succeeds', async () => {
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      productionLanguageCode: 'en-US'
    });
    component.storyTextControl.setValue('Mara: We go now.');

    await component.analyzeStory();
    fixture.detectChanges();

    expect(audiobookWorkflowService.analyzeSpeakers).toHaveBeenCalledWith('Mara: We go now.', undefined);
    expect(fixture.nativeElement.textContent).toContain('Mara');
    expect(fixture.nativeElement.textContent).toContain('Dialogue speaker');
    expect(fixture.nativeElement.textContent).toContain('WARM ALTO VOICE');
    expect(fixture.nativeElement.textContent).toContain('Cast needs review');
    expect(fixture.nativeElement.textContent).toContain('The Hidden Signal');
  });

  it('shows and saves the AI project title from the first workflow step', async () => {
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      productionLanguageCode: 'en-US'
    });
    component.storyTextControl.setValue('Mara: We go now.');

    await component.analyzeStory();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('The Hidden Signal');
    expect(fixture.nativeElement.querySelector('[data-testid="edit-project-title"]')).not.toBeNull();

    clickButton('Edit title');
    fixture.detectChanges();

    setInputValue('#project-title', 'Updated Signal');
    audiobookLibraryService.updateTitle.and.resolveTo({
      id: 'project-1',
      title: 'Updated Signal',
      status: 'NEEDS_REVIEW',
      speechSegmentCount: 0,
      speakerCount: null,
      totalDurationSeconds: null,
      createdAt: '2026-05-12T10:00:00Z',
      updatedAt: '2026-05-12T10:02:00Z',
      speechSegments: [],
      audioAssets: []
    } as never);

    clickButton('Save title');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(audiobookLibraryService.updateTitle).toHaveBeenCalledWith('project-1', 'Updated Signal');
    expect(component.projectTitleControl.value).toBe('Updated Signal');
    expect(fixture.nativeElement.textContent).toContain('Updated Signal');
  });

  it('shows an edited cast speaker name after saving the cast card', async () => {
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
    ];
    fixture.detectChanges();

    // Simulate editing a cast member by updating the component's cast directly
    // (which is how the facade state gets updated when form changes are applied)
    component.cast = [
      { speakerName: 'Captain Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
    ];
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Captain Mara');
    expect(component.cast[0].speakerName).toBe('Captain Mara');
  });

  it('formats compact speaker names for display', () => {
    expect(formatSpeakerDisplayName('StationKeeper')).toBe('Station Keeper');
    expect(formatSpeakerDisplayName('station_keeper')).toBe('Station Keeper');
    expect(formatSpeakerDisplayName('station-keeper')).toBe('Station Keeper');
  });

  it('uses edited cast data when continuing to script preview', async () => {
    component.storyTextControl.setValue('StationKeeper: All aboard.');
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [
        { speakerName: 'StationKeeper', roleDescription: 'Old role', voiceSuggestion: 'Old voice' }
      ],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      productionLanguageCode: 'en-US'
    });
    await component.analyzeStory();
    fixture.detectChanges();
    audiobookWorkflowService.approveCast.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'StationKeeper: All aboard.',
      workflowStage: 'CAST_APPROVED',
      speakers: [
        { speakerName: 'Station Keeper', roleDescription: 'Caretaker of the midnight platform', voiceSuggestion: 'Warm gravelly voice' }
      ],
      scriptTurns: [],
      annotatedTurns: [],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);

    getByTestId('cast-edit-0').click();
    fixture.detectChanges();
    setInputValue('#cast-speaker-name-0', 'Station Keeper');
    setInputValue('#cast-role-description-0', 'Caretaker of the midnight platform');
    setInputValue('#cast-voice-suggestion-0', 'Warm gravelly voice');
    clickButton('Save');
    fixture.detectChanges();

    audiobookWorkflowService.splitDialogue.and.resolveTo([
      { speaker: 'Station Keeper', text: 'All aboard.' }
    ]);

    await component.approveCast();
    await component.createScriptPreview();

    expect(audiobookWorkflowService.approveCast).toHaveBeenCalledWith('project-1');
    expect(audiobookWorkflowService.splitDialogue).toHaveBeenCalledWith(component.storyTextControl.value, [
      {
        speakerName: 'Station Keeper',
        roleDescription: 'Caretaker of the midnight platform',
        voiceSuggestion: 'Warm gravelly voice'
      }
    ], 'project-1', undefined);
  });

  it('shows script preview turns after cast analysis continues', async () => {
    component.storyTextControl.setValue('Mara: We go now.\nJonas: Together.');
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [
        { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
        { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
      ],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      productionLanguageCode: 'en-US'
    });
    await component.analyzeStory();
    fixture.detectChanges();
    audiobookWorkflowService.approveCast.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'Mara: We go now.\nJonas: Together.',
      workflowStage: 'CAST_APPROVED',
      speakers: [
        { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
        { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
      ],
      scriptTurns: [],
      annotatedTurns: [],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);
    audiobookWorkflowService.splitDialogue.and.resolveTo([
      { speaker: 'Mara', text: 'We go now.' },
      { speaker: 'Jonas', text: 'Together.' }
    ]);

    await component.approveCast();
    await component.createScriptPreview();
    fixture.detectChanges();

    expect(audiobookWorkflowService.approveCast).toHaveBeenCalledWith('project-1');
    expect(audiobookWorkflowService.splitDialogue).toHaveBeenCalledWith(component.storyTextControl.value, component.cast, 'project-1', undefined);
    expect(fixture.nativeElement.textContent).toContain('Review script');
    expect(fixture.nativeElement.textContent).toContain('We go now.');
    expect(fixture.nativeElement.textContent).toContain('Together.');
    expect(fixture.nativeElement.textContent).toContain('Script needs your approval');
  });


  it('shows an edited script turn after saving the speaker and text', async () => {
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
      { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
    ];
    component.scriptTurns = [{ speaker: 'Mara', text: 'We go now.' }];
    (component as any).facade.setCurrentProjectId('project-1');
    fixture.detectChanges();

    component.startScriptTurnEdit(0);
    fixture.detectChanges();

    component.scriptTurnEditDraft!.speaker = 'Jonas';
    component.scriptTurnEditDraft!.text = 'We wait for the signal.';
    await component.saveScriptTurnEdit(0);
    fixture.detectChanges();

    expect(component.scriptTurns[0]).toEqual({ speaker: 'Jonas', text: 'We wait for the signal.' });
    expect(fixture.nativeElement.textContent).toContain('Jonas');
    expect(fixture.nativeElement.textContent).toContain('We wait for the signal.');
  });

  it('opens the first script turn editor without activating cast editing', () => {
    component.cast = [
      { speakerName: 'Narrator', roleDescription: 'Story voice', voiceSuggestion: 'Clear narrator' },
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
    ];
    component.scriptTurns = [
      { speaker: 'Narrator', text: 'The last train had already left.' },
      { speaker: 'Mara', text: 'Jonas, tell me you did not hide this here all winter.' }
    ];
    fixture.detectChanges();

    getByTestId('script-turn-edit-0').click();
    fixture.detectChanges();

    expect(component.editingScriptTurnIndex).toBe(0);
    expect(component.editingCastIndex).toBeNull();
    expect(fixture.nativeElement.querySelector('#script-speaker-0')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#script-text-0')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#cast-speaker-name-0')).toBeNull();
  });

  it('disables the production plan button when script edits make performance notes stale', async () => {
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
    ];
    component.scriptTurns = [{ speaker: 'Mara', text: 'We go now.' }];
    component.scriptApproved = true;
    component.annotatedTurns = [{ speaker: 'Mara', text: '[urgent] We go now.' }];
    (component as any).facade.setCurrentProjectId('project-1');
    fixture.detectChanges();

    // Keep the performance section expanded for the interaction below. It is not
    // auto-collapsed here (no audio production plan yet, so isCompleted is false),
    // but make the intent explicit and robust to ordering changes.
    (fixture.debugElement.query(By.directive(PerformanceNotesComponent)).componentInstance as PerformanceNotesComponent).collapsed.set(false);

    // Script section auto-collapsed because scriptApproved=true; expand it before interacting.
    (getByTestId('script-section').querySelector('button') as HTMLElement).click();
    fixture.detectChanges();

    getByTestId('script-turn-edit-0').click();
    fixture.detectChanges();
    setInputValue('#script-text-0', 'We go at sunrise.');
    clickButton('Save');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const planButton = buttonByText('Next: Prepare audiobook');
    expect(fixture.nativeElement.textContent).toContain('Script changed. Update the emotion & pacing before generating the audiobook.');
    expect(planButton.disabled).toBeTrue();

    audiobookWorkflowService.approveScript.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'Mara: We go at sunrise.',
      workflowStage: 'SCRIPT_APPROVED',
      speakers: [
        { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
      ],
      scriptTurns: [{ speaker: 'Mara', text: 'We go at sunrise.' }],
      annotatedTurns: [],
      audioAssets: [],
      performanceNotesStale: true
    } as never);
    audiobookWorkflowService.annotateEmotions.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'Mara: We go at sunrise.',
      workflowStage: 'PERFORMANCE_READY',
      speakers: [
        { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
      ],
      scriptTurns: [{ speaker: 'Mara', text: 'We go at sunrise.' }],
      annotatedTurns: [{ speaker: 'Mara', text: '[hopeful] We go at sunrise.' }],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);
    clickButton('Identify Emotions');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.performanceNotesStale).toBeFalse();
    expect(component.performanceReady).toBeTrue();
    expect(buttonByText('Next: Prepare audiobook').disabled).toBeFalse();
  });

  it('continues the emotion annotation flow after the user approves the script', async () => {
    component.cast = [{ speakerName: 'Narrator', roleDescription: 'Story voice', voiceSuggestion: 'Clear narrator' }];
    component.scriptTurns = [{ speaker: 'Narrator', text: 'The lamps dimmed.' }];
    (component as any).facade.setCurrentProjectId('project-1');
    component.castReviewed = true;
    audiobookWorkflowService.approveScript.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'The lamps dimmed.',
      workflowStage: 'SCRIPT_APPROVED',
      speakers: [{ speakerName: 'Narrator', roleDescription: 'Story voice', voiceSuggestion: 'Clear narrator' }],
      scriptTurns: [{ speaker: 'Narrator', text: 'The lamps dimmed.' }],
      annotatedTurns: [],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);
    audiobookWorkflowService.annotateEmotions.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'The lamps dimmed.',
      workflowStage: 'PERFORMANCE_READY',
      speakers: [{ speakerName: 'Narrator', roleDescription: 'Story voice', voiceSuggestion: 'Clear narrator' }],
      scriptTurns: [{ speaker: 'Narrator', text: 'The lamps dimmed.' }],
      annotatedTurns: [{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);
    fixture.detectChanges();

    await component.approveScript();
    fixture.detectChanges();
    await component.createPerformanceNotes();
    fixture.detectChanges();

    expect(audiobookWorkflowService.annotateEmotions).toHaveBeenCalledWith('project-1', undefined);
    expect(component.annotatedTurns).toEqual([{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }]);
  });


  it('skips re-approving the script when re-running emotion annotation on an already-approved project', async () => {
    component.scriptTurns = [{ speaker: 'Narrator', text: 'The lamps dimmed.' }];
    (component as any).facade.setCurrentProjectId('project-1');
    (component as any).facade.setScriptApproved(true);
    audiobookWorkflowService.annotateEmotions.and.resolveTo({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      storyText: 'The lamps dimmed.',
      workflowStage: 'PERFORMANCE_READY',
      speakers: [{ speakerName: 'Narrator', roleDescription: 'Story voice', voiceSuggestion: 'Clear narrator' }],
      scriptTurns: [{ speaker: 'Narrator', text: 'The lamps dimmed.' }],
      annotatedTurns: [{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      performanceNotesStale: false
    } as never);
    fixture.detectChanges();

    await component.approveScriptAndContinueWorkflow();
    fixture.detectChanges();

    expect(audiobookWorkflowService.approveScript).not.toHaveBeenCalled();
    expect(audiobookWorkflowService.annotateEmotions).toHaveBeenCalledWith('project-1', undefined);
    expect(component.annotatedTurns).toEqual([{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }]);
  });

  it('cancels an in-flight part generation and keeps already generated parts', async () => {
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} },
        { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} }
      ]
    };
    prepareGeneratedPart(0, 'part-1.mp3', 'part one');
    audiobookApiService.createAudioForRenderRequest.and.callFake((_renderRequest, options) => {
      return new Promise<never>((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => reject(abortError()), { once: true });
      });
    });

    const inFlight = component.generateAudioForRenderRequest(component.renderRequests[1], 1);
    fixture.detectChanges();
    expect(component.renderRequestAudioState(1).status).toBe('generating');

    component.cancelRenderRequestGeneration(1);
    await inFlight;
    fixture.detectChanges();

    expect(component.renderRequestAudioState(0).status).toBe('generated');
    expect(component.renderRequestAudioState(1).status).toBe('canceled');
    expect(component.renderRequestAudioState(1).error).toBe('Generation canceled. You can retry this part.');
    expect(fixture.nativeElement.textContent).toContain('Canceled - retry available');
  });

  it('marks a stuck part as timed out', fakeAsync(() => {
    component.partGenerationTimeoutMs = 5;
    component.audioProductionPlan = {
      renderRequests: [{ input: { text: 'Only part' }, voice: { name: 'Kore' }, audioConfig: {} }]
    };
    audiobookApiService.createAudioForRenderRequest.and.callFake((_renderRequest, options) => {
      return new Promise<never>((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => reject(abortError()), { once: true });
      });
    });

    void component.generateAudioForRenderRequest(component.renderRequests[0], 0);
    tick(6);
    flushMicrotasks();
    fixture.detectChanges();

    expect(component.renderRequestAudioState(0).status).toBe('timed-out');
    expect(component.renderRequestAudioState(0).error).toBe('This part took too long and was stopped. Try again or edit the text.');
  }));

  it('prevents duplicate generation requests for the same part', async () => {
    component.audioProductionPlan = {
      renderRequests: [{ input: { text: 'Only part' }, voice: { name: 'Kore' }, audioConfig: {} }]
    };
    audiobookApiService.createAudioForRenderRequest.and.callFake((_renderRequest, options) => {
      return new Promise<never>((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => reject(abortError()), { once: true });
      });
    });

    const first = component.generateAudioForRenderRequest(component.renderRequests[0], 0);
    const second = component.generateAudioForRenderRequest(component.renderRequests[0], 0);

    expect(audiobookApiService.createAudioForRenderRequest).toHaveBeenCalledTimes(1);
    component.cancelRenderRequestGeneration(0);
    await Promise.all([first, second]);
  });

  it('cancels full generation without discarding ready parts and resumes the missing part later', async () => {
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} },
        { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} },
        { input: { text: 'Third' }, voice: { name: 'Rasalgethi' }, audioConfig: {} }
      ]
    };
    prepareGeneratedPart(0, 'part-1.mp3', 'part one');
    prepareGeneratedPart(1, 'part-2.mp3', 'part two');
    audiobookApiService.createAudioForRenderRequest.and.callFake((_renderRequest, options) => {
      return new Promise<never>((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => reject(abortError()), { once: true });
      });
    });

    const firstRun = component.generateAudio();
    fixture.detectChanges();
    expect(component.fullPlanAudioLoading).toBeTrue();
    expect(component.renderRequestAudioState(2).status).toBe('generating');

    component.cancelFullAudioGeneration();
    await firstRun;
    fixture.detectChanges();

    expect(component.renderRequestAudioState(0).status).toBe('generated');
    expect(component.renderRequestAudioState(1).status).toBe('generated');
    expect(component.renderRequestAudioState(2).status).toBe('canceled');
    expect(component.fullPlanAudioLoading).toBeFalse();
    expect(component.fullPlanAudioError).toBe('Audiobook preview generation was canceled.');
    expect(component.currentGenerationStatusLabel()).toBe('Audiobook preview generation was canceled.');

    audiobookApiService.createAudioForRenderRequest.and.resolveTo({
      blob: new Blob(['part three'], { type: 'audio/mpeg' }),
      filename: 'part-3.mp3'
    });
    audiobookApiService.createAudioForRenderRequest.calls.reset();

    await component.generateAudio();
    fixture.detectChanges();

    expect(audiobookApiService.createAudioForRenderRequest).toHaveBeenCalledTimes(1);
    expect(audiobookApiService.createAudioForRenderRequest).toHaveBeenCalledWith(
      component.renderRequests[2],
      jasmine.objectContaining({ signal: jasmine.any(AbortSignal) }),
      undefined,
      2
    );
    expect(component.renderRequestAudioState(2).status).toBe('generated');
    expect(component.fullPlanAudioUrl).not.toBeNull();
  });

  it('shows the framework audio player and download action after the audiobook preview is generated', async () => {
    const anchorClickSpy = spyOn(HTMLAnchorElement.prototype, 'click');
    component.audioProductionPlan = {
      renderRequests: [{ input: {}, voice: { name: 'Kore' }, audioConfig: {} }]
    };
    audiobookApiService.createAudioForRenderRequest.and.resolveTo({
      blob: new Blob(['fake mp3'], { type: 'audio/mpeg' }),
      filename: 'part.mp3'
    });

    await component.generateAudio();
    fixture.detectChanges();

    const player = fixture.nativeElement.querySelector('.generated-audio-player .waveform-canvas') as HTMLElement | null;
    expect(audiobookApiService.createAudioForRenderRequest).toHaveBeenCalledWith(
      component.renderRequests[0],
      jasmine.objectContaining({ signal: jasmine.any(AbortSignal) }),
      undefined,
      0
    );
    expect(player).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Audiobook preview');
    expect(fixture.nativeElement.textContent).toContain('Download MP3');
    expect(component.fullPlanAudioFilename).toBe('audiobook-preview-merged.mp3');
    expect(anchorClickSpy).not.toHaveBeenCalled();
  });

  it('confirms the workflow after merging the full audiobook preview', async () => {
    component.audioProductionPlan = {
      renderRequests: [{ input: {}, voice: { name: 'Kore' }, audioConfig: {} }]
    };
    audiobookApiService.createAudioForRenderRequest.and.resolveTo({
      blob: new Blob(['fake mp3'], { type: 'audio/mpeg' }),
      filename: 'part.mp3',
      projectId: 'project-1'
    });
    (component as any).facade.setCurrentProjectId('project-1');

    await component.generateAudio();
    fixture.detectChanges();

    expect(audiobookWorkflowService.markAudioGenerated).toHaveBeenCalledWith('project-1');
    expect(fixture.nativeElement.textContent).toContain('Audiobook preview');
    expect(fixture.nativeElement.textContent).toContain('Ready to listen');
  });

  it('generating one part stores only that part and does not start a download', async () => {
    const anchorClickSpy = spyOn(HTMLAnchorElement.prototype, 'click');
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} },
        { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} }
      ]
    };
    audiobookApiService.createAudioForRenderRequest.and.resolveTo({
      blob: new Blob(['part one'], { type: 'audio/mpeg' }),
      filename: 'part-1.mp3'
    });

    await component.generateAudioForRenderRequest(component.renderRequests[0], 0);
    fixture.detectChanges();

    expect(component.renderRequestAudioState(0).status).toBe('generated');
    expect(component.renderRequestAudioState(0).blob).not.toBeNull();
    expect(component.renderRequestAudioState(1).status).toBe('not-generated');
    expect(anchorClickSpy).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Ready to listen');
    expect(fixture.nativeElement.textContent).toContain('Download part');
  });

  it('generating the audiobook skips already generated parts', async () => {
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} },
        { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} },
        { input: { text: 'Third' }, voice: { name: 'Kore' }, audioConfig: {} }
      ]
    };
    audiobookApiService.createAudioForRenderRequest.and.resolveTo({
      blob: new Blob(['mp3'], { type: 'audio/mpeg' }),
      filename: 'part.mp3'
    });

    await component.generateAudioForRenderRequest(component.renderRequests[0], 0);
    await component.generateAudioForRenderRequest(component.renderRequests[1], 1);
    audiobookApiService.createAudioForRenderRequest.calls.reset();

    await component.generateAudio();

    expect(audiobookApiService.createAudioForRenderRequest).toHaveBeenCalledTimes(1);
    expect(audiobookApiService.createAudioForRenderRequest).toHaveBeenCalledWith(
      component.renderRequests[2],
      jasmine.objectContaining({ signal: jasmine.any(AbortSignal) }),
      undefined,
      2
    );
    expect(component.fullPlanAudioUrl).not.toBeNull();
  });

  it('explains how many audiobook parts are ready before merging', async () => {
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} },
        { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} },
        { input: { text: 'Third' }, voice: { name: 'Rasalgethi' }, audioConfig: {} }
      ]
    };
    component.renderRequestAudioState(1).status = 'failed';
    fixture.detectChanges();

    expect(component.audiobookReadinessSummary()).toBe('0 of 3 parts ready - 2 missing, 1 failed');
    expect(component.currentGenerationStatusLabel()).toBe('0 of 3 parts ready - 2 missing, 1 failed');
    expect(component.currentGenerationDetails()).toBe('Generate audiobook preview will use the parts that are already ready and only generate the 3 missing, canceled, failed, or timed-out parts before merging.');
  });

  it('leads render part cards with the speaker identity mapped from the voice', () => {
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Determined lead', voiceSuggestion: 'Kore' },
      { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Iapetus' }
    ];
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'Then we go now.' }, voice: { name: 'Kore' }, audioConfig: {} }
      ]
    };
    fixture.detectChanges();

    expect(component.renderRequestSpeakerName(0)).toBe('Mara');
    expect(fixture.nativeElement.textContent).toContain('Part 1 of 1');
    expect(fixture.nativeElement.textContent).toContain('Determined lead');
  });

  it('keeps successful parts when one audiobook part fails', async () => {
    component.audioProductionPlan = {
      renderRequests: [
        { input: { text: 'First' }, voice: { name: 'Kore' }, audioConfig: {} },
        { input: { text: 'Second' }, voice: { name: 'Iapetus' }, audioConfig: {} },
        { input: { text: 'Third' }, voice: { name: 'Kore' }, audioConfig: {} }
      ]
    };
    audiobookApiService.createAudioForRenderRequest.and.callFake(async (renderRequest) => {
      if (renderRequest === component.renderRequests[1]) {
        throw new Error('Provider exploded');
      }
      return {
        blob: new Blob(['mp3'], { type: 'audio/mpeg' }),
        filename: 'part.mp3'
      };
    });

    await component.generateAudio();
    fixture.detectChanges();

    expect(component.renderRequestAudioState(0).status).toBe('generated');
    expect(component.renderRequestAudioState(1).status).toBe('failed');
    expect(component.renderRequestAudioState(2).status).toBe('generated');
    expect(component.fullPlanAudioUrl).toBeNull();
    expect(component.currentGenerationStatusLabel()).toBe('1 part failed to generate. Please retry them individually before generating the final preview.');
    expect(component.currentGenerationDetails()).toBe('Generate audiobook preview will use the parts that are already ready and only generate the 1 missing, canceled, failed, or timed-out part before merging.');
  });

  it('shows a user-facing backend error when analysis fails', async () => {
    audiobookWorkflowService.analyzeSpeakers.and.rejectWith(new Error('The cast analysis provider is currently unavailable. Please try again later.'));
    component.storyTextControl.setValue('Mara: Hello');

    await component.analyzeStory();
    fixture.detectChanges();

    expect(component.error).toBe('The cast analysis provider is currently unavailable. Please try again later.');
  });

  it('renders every leading performance tag as inline markup', () => {
    const markup = component.markupFor({ speaker: 'Mara', text: '[serious] [curious] Jonas, listen.' });

    expect(markup.tags).toEqual(['serious', 'curious']);
    expect(markup.text).toBe('Jonas, listen.');
  });

  it('preserves in-text tags in the remaining text for highlighting', () => {
    const markup = component.markupFor({ speaker: 'Mara', text: '[sigh] [curious] Jonas, [short pause] tell me you did not hide this.' });

    expect(markup.tags).toEqual(['sigh', 'curious']);
    expect(markup.text).toContain('[short pause]');
    expect(markup.text).toBe('Jonas, [short pause] tell me you did not hide this.');
  });

  it('exposes the persisted merged audio URL from the workflow snapshot via facade signal', () => {
    component.hydrateFromSnapshot({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      storyText: 'Mara: We go now.',
      workflowStage: 'AUDIO_GENERATED',
      speakers: [],
      scriptTurns: [],
      annotatedTurns: [],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      audioAssetsCurrent: true,
      performanceNotesStale: false,
      mergedAudioUrl: '/api/audiobooks/project-1/audio-assets/merged-1/stream'
    });

    expect((component as any).facade.mergedAudioUrl()).toBe('/api/audiobooks/project-1/audio-assets/merged-1/stream');
  });

  it('shows the generated preview player after page reload using the persisted merged audio URL', () => {
    component.hydrateFromSnapshot({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      storyText: 'Mara: We go now.',
      workflowStage: 'AUDIO_GENERATED',
      speakers: [],
      scriptTurns: [],
      annotatedTurns: [],
      audioAssets: [],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      audioAssetsCurrent: true,
      performanceNotesStale: false,
      mergedAudioUrl: '/api/audiobooks/project-1/audio-assets/merged-1/stream'
    });
    fixture.detectChanges();

    expect(component.fullPlanAudioUrl).toBe('/api/audiobooks/project-1/audio-assets/merged-1/stream');
    const player = fixture.nativeElement.querySelector('.generated-audio-player') as HTMLElement | null;
    expect(player).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Audiobook preview');
    expect(fixture.nativeElement.textContent).toContain('Download MP3');
  });

  it('keeps the performance step completed and collapsed after reload when only persisted audio assets exist', () => {
    component.hydrateFromSnapshot({
      projectId: 'project-1',
      title: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      storyText: 'Mara: We go now.',
      workflowStage: 'AUDIO_GENERATED',
      speakers: [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'KORE' }],
      scriptTurns: [{ speaker: 'Mara', text: 'We go now.' }],
      annotatedTurns: [{ speaker: 'Mara', text: '[urgent] We go now.' }],
      audioAssets: [
        {
          id: 'asset-mara-1',
          speechSegmentId: 'segment-mara-1',
          type: 'PREVIEW_MP3',
          version: 1,
          filename: 'mara-part-1.mp3',
          contentType: 'audio/mpeg',
          sizeBytes: 42000,
          durationSeconds: null,
          status: 'READY',
          createdAt: '2026-05-12T10:00:00Z',
          downloadUrl: 'https://example.com/audio/download/mara-part-1.mp3',
          streamUrl: 'https://example.com/audio/mara-part-1.mp3',
          speakerName: 'Mara'
        }
      ],
      productionSettings: {
        prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
        languageCode: 'en-US',
        modelName: 'gemini-3.1-flash-tts-preview',
        audioEncoding: 'MP3'
      },
      audioAssetsCurrent: true,
      performanceNotesStale: false
      // NOTE: no mergedAudioUrl and no regenerated production plan on reload.
    });
    fixture.detectChanges();

    expect(component.performanceStepCompleted).toBeTrue();

    const performanceSection = getByTestId('performance-section');
    const stepNumber = performanceSection.querySelector('.step-number') as HTMLElement;
    expect(stepNumber.classList).toContain('step-number-done');

    const performanceNotes = fixture.debugElement.query(
      By.directive(PerformanceNotesComponent)
    ).componentInstance as PerformanceNotesComponent;
    expect(performanceNotes.collapsed()).toBeTrue();
  });

  function buttonByText(text: string, occurrence = 0): HTMLButtonElement {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const button = buttons.filter((candidate) => candidate.textContent?.trim() === text)[occurrence];

    if (!button) {
      throw new Error(`Could not find button with text: ${text}`);
    }

    return button;
  }

  function clickButton(text: string, occurrence = 0): void {
    buttonByText(text, occurrence).click();
  }

  function setInputValue(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function setSelectValue(selector: string, value: string): void {
    const select = fixture.nativeElement.querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  function getByTestId(testId: string): HTMLElement {
    const element = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;

    if (!element) {
      throw new Error(`Could not find element with data-testid: ${testId}`);
    }

    return element;
  }

  function abortError(): DOMException {
    return new DOMException('Aborted', 'AbortError');
  }

  // ── Voice picker ────────────────────────────────────────────────────────────

  describe('voice picker', () => {
    beforeEach(() => {
      component.cast = [
        { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'ZEPHYR' },
        { speakerName: 'Jonas', roleDescription: 'The guide', voiceSuggestion: 'PUCK' },
      ];
      fixture.detectChanges();
    });

    it('opens the voice picker modal when Change voice is clicked', () => {
      const changeBtn = fixture.nativeElement.querySelector('[data-testid="cast-change-voice-0"]');
      expect(changeBtn).not.toBeNull();
      changeBtn.click();
      fixture.detectChanges();

      expect(component.voicePickerOpenForIndex).toBe(0);
      const modal = fixture.nativeElement.querySelector('[data-testid="voice-picker-modal"]');
      expect(modal).not.toBeNull();
    });

    it('passes the correct speaker name to the modal', () => {
      component.openVoicePicker(0);
      fixture.detectChanges();

      expect(component.voicePickerSpeakerName()).toBe('Mara');
    });

    it('passes the current voice id (lowercase) to the modal', () => {
      component.openVoicePicker(0);
      expect(component.voicePickerCurrentVoiceId()).toBe('zephyr');
    });

    it('updates voiceSuggestion when a voice is selected', fakeAsync(() => {
      component.openVoicePicker(0);
      fixture.detectChanges();

      component.applyVoiceSelection({ id: 'puck', providerVoiceName: 'Puck', displayName: 'Puck', description: 'Playful.', imageUrl: '', demoMp3Url: '', gender: 'MALE' });
      fixture.detectChanges();

      expect(component.cast[0].voiceSuggestion).toBe('PUCK');
      flush();
    }));

    it('shows the updated voice on the cast card immediately after selection', fakeAsync(() => {
      component.openVoicePicker(0);
      fixture.detectChanges();

      component.applyVoiceSelection({ id: 'puck', providerVoiceName: 'Puck', displayName: 'Puck', description: 'Playful.', imageUrl: '', demoMp3Url: '', gender: 'MALE' });
      fixture.detectChanges();

      const voiceBadge = fixture.nativeElement.querySelector('.voice-name-display');
      expect(voiceBadge.textContent.trim()).toBe('PUCK');
      flush();
    }));

    it('closes the voice picker when closeVoicePicker is called', () => {
      component.openVoicePicker(0);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="voice-picker-modal"]')).not.toBeNull();

      component.closeVoicePicker();
      fixture.detectChanges();
      expect(component.voicePickerOpenForIndex).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="voice-picker-modal"]')).toBeNull();
    });
  });

  function prepareGeneratedPart(index: number, filename: string, body: string): void {
    const state = component.renderRequestAudioState(index);
    state.status = 'generated';
    state.blob = new Blob([body], { type: 'audio/mpeg' });
    state.audioUrl = `blob:${filename}`;
    state.filename = filename;
    state.generatedAt = new Date();
    state.requestId = 0;
    state.startedAt = null;
    state.timeoutHandle = null;
    state.controller = null;
    state.cancelReason = null;
    state.inFlightPromise = null;
  }
});
