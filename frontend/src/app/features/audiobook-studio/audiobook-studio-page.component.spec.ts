import { ComponentFixture, fakeAsync, TestBed, tick, flushMicrotasks } from '@angular/core/testing';
import { AudiobookStudioPageComponent, formatSpeakerDisplayName } from './audiobook-studio-page.component';
import { AudiobookApiService } from '../audiobook-shared/service/audiobook-api.service';
import { AudiobookWorkflowService } from '../audiobook-shared/service/audiobook-workflow.service';
import { AudiobookLibraryService } from '../audiobook-library/services/audiobook-library.service';

describe('AudiobookStudioPageComponent', () => {
  let fixture: ComponentFixture<AudiobookStudioPageComponent>;
  let component: AudiobookStudioPageComponent;
  let audiobookWorkflowService: jasmine.SpyObj<AudiobookWorkflowService>;
  let audiobookApiService: jasmine.SpyObj<AudiobookApiService>;
  let audiobookLibraryService: jasmine.SpyObj<AudiobookLibraryService>;

  beforeEach(async () => {
    audiobookWorkflowService = jasmine.createSpyObj<AudiobookWorkflowService>('AudiobookWorkflowService', [
      'analyzeSpeakers',
      'splitDialogue',
      'saveScriptPreview',
      'annotateEmotions',
      'generateFinalJson',
      'planSingleSpeakerRenderRequests',
      'createAudio'
    ]);
    audiobookApiService = jasmine.createSpyObj<AudiobookApiService>('AudiobookApiService', [
      'createAudio',
      'createAudioForRenderRequest',
      'post',
      'postJsonResponse',
      'postBlobResponse'
    ]);
    audiobookLibraryService = jasmine.createSpyObj<AudiobookLibraryService>('AudiobookLibraryService', ['updateTitle']);
    audiobookApiService.createAudioForRenderRequest.and.callFake(async () => ({
      blob: new Blob(['generated'], { type: 'audio/mpeg' }),
      filename: 'tts-render-request-1.mp3'
    }));
    audiobookWorkflowService.saveScriptPreview.and.callFake(async (_projectId, turns) => turns);
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [],
      projectId: null,
      projectTitle: ''
    });
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
      imports: [AudiobookStudioPageComponent],
      providers: [
        { provide: AudiobookWorkflowService, useValue: audiobookWorkflowService },
        { provide: AudiobookApiService, useValue: audiobookApiService },
        { provide: AudiobookLibraryService, useValue: audiobookLibraryService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookStudioPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('fills the textarea when the sample story is selected', () => {
    clickButton('Use sample story');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('#story-text') as HTMLTextAreaElement;
    expect(textarea.value).toContain('Mara');
    expect(textarea.value).toContain('Jonas');
    expect(textarea.value).toContain('Station Keeper');
  });

  it('shows cast cards after story analysis succeeds', async () => {
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal'
    });
    component.storyTextControl.setValue('Mara: We go now.');

    await component.analyzeStory();
    fixture.detectChanges();

    expect(audiobookWorkflowService.analyzeSpeakers).toHaveBeenCalledWith('Mara: We go now.');
    expect(fixture.nativeElement.textContent).toContain('Mara');
    expect(fixture.nativeElement.textContent).toContain('Detected character');
    expect(fixture.nativeElement.textContent).toContain('Warm alto voice');
    expect(fixture.nativeElement.textContent).toContain('Cast needs review');
    expect(fixture.nativeElement.textContent).toContain('The Hidden Signal');
  });

  it('shows and saves the AI project title from the first workflow step', async () => {
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal'
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

    clickButton('Edit');
    fixture.detectChanges();

    setInputValue('#cast-speaker-name-0', 'Captain Mara');
    clickButton('Save');
    fixture.detectChanges();

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
      projectTitle: 'The Hidden Signal'
    });
    await component.analyzeStory();
    fixture.detectChanges();

    clickButton('Edit');
    fixture.detectChanges();
    setInputValue('#cast-speaker-name-0', 'Station Keeper');
    setInputValue('#cast-role-description-0', 'Caretaker of the midnight platform');
    setInputValue('#cast-voice-suggestion-0', 'Warm gravelly voice');
    clickButton('Save');
    fixture.detectChanges();

    audiobookWorkflowService.splitDialogue.and.resolveTo([
      { speaker: 'Station Keeper', text: 'All aboard.' }
    ]);

    await component.createScriptPreview();

    expect(audiobookWorkflowService.splitDialogue).toHaveBeenCalledWith(component.storyTextControl.value, [
      {
        speakerName: 'Station Keeper',
        roleDescription: 'Caretaker of the midnight platform',
        voiceSuggestion: 'Warm gravelly voice'
      }
    ], 'project-1');
  });

  it('shows script preview turns after cast analysis continues', async () => {
    component.storyTextControl.setValue('Mara: We go now.\nJonas: Together.');
    audiobookWorkflowService.analyzeSpeakers.and.resolveTo({
      speakers: [
        { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
        { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
      ],
      projectId: 'project-1',
      projectTitle: 'The Hidden Signal'
    });
    await component.analyzeStory();
    fixture.detectChanges();
    audiobookWorkflowService.splitDialogue.and.resolveTo([
      { speaker: 'Mara', text: 'We go now.' },
      { speaker: 'Jonas', text: 'Together.' }
    ]);

    await component.createScriptPreview();
    fixture.detectChanges();

    expect(audiobookWorkflowService.splitDialogue).toHaveBeenCalledWith(component.storyTextControl.value, component.cast, 'project-1');
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

    clickButton('Edit', 1);
    fixture.detectChanges();
    setInputValue('#script-text-0', 'We go at sunrise.');
    clickButton('Save');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const planButton = buttonByText('Next: Prepare audiobook');
    expect(fixture.nativeElement.textContent).toContain('Script changed. Update the emotion & pacing before generating the audiobook.');
    expect(planButton.disabled).toBeTrue();

    clickButton('Approve script & continue');
    audiobookWorkflowService.annotateEmotions.and.resolveTo([{ speaker: 'Mara', text: '[hopeful] We go at sunrise.' }]);
    await component.createPerformanceNotes();
    fixture.detectChanges();

    expect(component.performanceNotesStale).toBeFalse();
    expect(buttonByText('Next: Prepare audiobook').disabled).toBeFalse();
  });

  it('continues the emotion annotation flow after the user approves the script', async () => {
    component.scriptTurns = [{ speaker: 'Narrator', text: 'The lamps dimmed.' }];
    (component as any).facade.setCurrentProjectId('project-1');
    audiobookWorkflowService.annotateEmotions.and.resolveTo([{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }]);
    fixture.detectChanges();

    expect(buttonByText('Add emotion & pacing').disabled).toBeTrue();

    clickButton('Approve script & continue');
    fixture.detectChanges();
    clickButton('Add emotion & pacing');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(audiobookWorkflowService.annotateEmotions).toHaveBeenCalledWith('project-1');
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
      undefined
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
      undefined
    );
    expect(player).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Audiobook preview');
    expect(fixture.nativeElement.textContent).toContain('Download MP3');
    expect(component.fullPlanAudioFilename).toBe('audiobook-preview-merged.mp3');
    expect(anchorClickSpy).not.toHaveBeenCalled();
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
      undefined
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

    expect(fixture.nativeElement.textContent).toContain('The cast analysis provider is currently unavailable. Please try again later.');
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
