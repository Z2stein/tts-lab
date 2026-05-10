import { TestBed } from '@angular/core/testing';
import { TtsWorkbenchService } from '../tts-workbench/tts-workbench.service';
import { WorkflowService } from './services/workflow.service';
import { AudiobookStudioFacade } from './audiobook-studio.facade';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from './services/render-request-audio.service';

describe('AudiobookStudioFacade', () => {
  let facade: AudiobookStudioFacade;
  let tts: jasmine.SpyObj<TtsWorkbenchService>;
  let workflow: jasmine.SpyObj<WorkflowService>;
  let renderSvc: jasmine.SpyObj<RenderRequestAudioService>;
  let fullSvc: jasmine.SpyObj<FullAudioGenerationService>;

  const maraItem = { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Kore' };
  const jonasItem = { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Iapetus' };

  beforeEach(() => {
    tts = jasmine.createSpyObj<TtsWorkbenchService>('TtsWorkbenchService', [
      'analyzeSpeakers', 'splitDialogue', 'annotateEmotions',
      'generateFinalJson', 'planSingleSpeakerRenderRequests',
      'createAudio', 'createAudioForRenderRequest',
    ]);
    workflow = jasmine.createSpyObj<WorkflowService>('WorkflowService', [
      'createWorkflow', 'getWorkflow', 'discoverSpeakers', 'splitDialogue',
      'annotateDialogue', 'configureOutput', 'generateAudio'
    ], {
      session$: jasmine.createSpyObj('Observable', ['subscribe'])
    });
    renderSvc = jasmine.createSpyObj<RenderRequestAudioService>('RenderRequestAudioService', ['abortAll', 'revokeUrls']);
    fullSvc = jasmine.createSpyObj<FullAudioGenerationService>('FullAudioGenerationService', ['clearAudio']);

    // Mock workflow service methods to return observables that resolve successfully
    (workflow.createWorkflow as jasmine.Spy).and.returnValue({ toPromise: () => Promise.resolve({ id: 'session-1' }) });
    (workflow.discoverSpeakers as jasmine.Spy).and.returnValue({ toPromise: () => Promise.resolve({}) });
    (workflow.splitDialogue as jasmine.Spy).and.returnValue({ toPromise: () => Promise.resolve({}) });
    (workflow.annotateDialogue as jasmine.Spy).and.returnValue({ toPromise: () => Promise.resolve({}) });
    (workflow.configureOutput as jasmine.Spy).and.returnValue({ toPromise: () => Promise.resolve({}) });

    TestBed.configureTestingModule({
      providers: [
        AudiobookStudioFacade,
        { provide: TtsWorkbenchService, useValue: tts },
        { provide: WorkflowService, useValue: workflow },
        { provide: RenderRequestAudioService, useValue: renderSvc },
        { provide: FullAudioGenerationService, useValue: fullSvc },
      ],
    });
    facade = TestBed.inject(AudiobookStudioFacade);
  });

  // ── analyzeStory ──────────────────────────────────────────────────────────

  it('sets cast and clears downstream state after analyzeStory succeeds', async () => {
    tts.analyzeSpeakers.and.resolveTo([maraItem]);
    facade.setScriptTurns([{ speaker: 'Mara', text: 'Hello' }]);
    facade.setAnnotatedTurns([{ speaker: 'Mara', text: '[warm] Hello' }]);
    facade.setCastReviewed(true);
    facade.setScriptApproved(true);

    await facade.analyzeStory('Some story text');

    expect(facade.cast()).toEqual([maraItem]);
    expect(facade.scriptTurns()).toEqual([]);
    expect(facade.annotatedTurns()).toEqual([]);
    expect(facade.castReviewed()).toBeFalse();
    expect(facade.scriptApproved()).toBeFalse();
    expect(facade.loadingAction()).toBeNull();
    expect(facade.error()).toBeNull();
  });

  it('sets error when analyzeStory fails', async () => {
    tts.analyzeSpeakers.and.rejectWith(new Error('Provider unavailable'));
    await facade.analyzeStory('Some text');
    expect(facade.error()).toBe('Provider unavailable');
    expect(facade.loadingAction()).toBeNull();
  });

  // ── createScriptPreview ───────────────────────────────────────────────────

  it('passes the current cast to splitDialogue and marks castReviewed', async () => {
    facade.setCast([maraItem, jonasItem]);
    tts.splitDialogue.and.resolveTo([{ speaker: 'Mara', text: 'We go.' }]);

    await facade.createScriptPreview('Mara: We go.');

    expect(tts.splitDialogue).toHaveBeenCalledWith('Mara: We go.', [maraItem, jonasItem]);
    expect(facade.scriptTurns()).toEqual([{ speaker: 'Mara', text: 'We go.' }]);
    expect(facade.castReviewed()).toBeTrue();
    expect(facade.scriptApproved()).toBeFalse();
  });

  // ── createPerformanceNotes ────────────────────────────────────────────────

  it('passes current scriptTurns to annotateEmotions and marks notes not stale', async () => {
    facade.setScriptTurns([{ speaker: 'Mara', text: 'We go.' }]);
    facade.setPerformanceNotesStale(true);
    tts.annotateEmotions.and.resolveTo([{ speaker: 'Mara', text: '[urgent] We go.' }]);

    await facade.createPerformanceNotes();

    expect(tts.annotateEmotions).toHaveBeenCalledWith([{ speaker: 'Mara', text: 'We go.' }]);
    expect(facade.annotatedTurns()).toEqual([{ speaker: 'Mara', text: '[urgent] We go.' }]);
    expect(facade.performanceNotesStale()).toBeFalse();
  });

  // ── approveScript ─────────────────────────────────────────────────────────

  it('sets scriptApproved to true', () => {
    expect(facade.scriptApproved()).toBeFalse();
    facade.approveScript();
    expect(facade.scriptApproved()).toBeTrue();
  });

  // ── Cast editing ──────────────────────────────────────────────────────────

  it('saveCastEdit replaces only the edited cast member', () => {
    facade.setCast([maraItem, jonasItem]);
    facade.startCastEdit(0);
    const draft = facade.castEditDraft()!;
    draft.speakerName = 'Captain Mara';
    facade.saveCastEdit(0);

    expect(facade.cast()[0].speakerName).toBe('Captain Mara');
    expect(facade.cast()[1]).toBe(jonasItem);
    expect(facade.editingCastIndex()).toBeNull();
  });

  it('cancelCastEdit clears draft and index', () => {
    facade.setCast([maraItem]);
    facade.startCastEdit(0);
    facade.cancelCastEdit();
    expect(facade.editingCastIndex()).toBeNull();
    expect(facade.castEditDraft()).toBeNull();
  });

  // ── Script turn editing ───────────────────────────────────────────────────

  it('saveScriptTurnEdit replaces the turn and un-approves the script', () => {
    facade.setScriptTurns([{ speaker: 'Mara', text: 'We go.' }]);
    facade.setScriptApproved(true);
    facade.startScriptTurnEdit(0);
    const draft = facade.scriptTurnEditDraft()!;
    draft.text = 'We stay.';
    facade.saveScriptTurnEdit(0);

    expect(facade.scriptTurns()[0].text).toBe('We stay.');
    expect(facade.scriptApproved()).toBeFalse();
  });

  it('saveScriptTurnEdit marks performance notes stale when they exist', () => {
    facade.setScriptTurns([{ speaker: 'Mara', text: 'We go.' }]);
    facade.setAnnotatedTurns([{ speaker: 'Mara', text: '[urgent] We go.' }]);
    facade.startScriptTurnEdit(0);
    const draft = facade.scriptTurnEditDraft()!;
    draft.text = 'We stay.';
    facade.saveScriptTurnEdit(0);

    expect(facade.performanceNotesStale()).toBeTrue();
    expect(fullSvc.clearAudio).toHaveBeenCalled();
  });

  // ── scriptGroups computed ─────────────────────────────────────────────────

  it('scriptGroups groups consecutive same-speaker turns', () => {
    facade.setScriptTurns([
      { speaker: 'Mara', text: 'Line 1.' },
      { speaker: 'Mara', text: 'Line 2.' },
      { speaker: 'Jonas', text: 'Reply.' },
    ]);

    const groups = facade.scriptGroups();
    expect(groups.length).toBe(2);
    expect(groups[0].speaker).toBe('Mara');
    expect(groups[0].turns.length).toBe(2);
    expect(groups[1].speaker).toBe('Jonas');
    expect(groups[1].turns.length).toBe(1);
  });

  // ── speakerOptions computed ───────────────────────────────────────────────

  it('speakerOptions includes cast names and adds Narrator when present in script', () => {
    facade.setCast([maraItem, jonasItem]);
    facade.setScriptTurns([{ speaker: 'Narrator', text: 'The lights dimmed.' }]);

    const options = facade.speakerOptions();
    expect(options).toContain('Mara');
    expect(options).toContain('Jonas');
    expect(options).toContain('Narrator');
  });

  // ── createAudioProductionPlan ────────────────────────────────────────────

  it('createAudioProductionPlan generates final request and plan', async () => {
    const finalRequest = { test: 'final' } as any;
    const plan = { test: 'plan' } as any;
    facade.setCast([maraItem]);
    facade.setAnnotatedTurns([{ speaker: 'Mara', text: '[warm] Hello' }]);
    tts.generateFinalJson.and.resolveTo(finalRequest);
    tts.planSingleSpeakerRenderRequests.and.resolveTo(plan);

    await facade.createAudioProductionPlan({
      prompt: 'test',
      languageCode: 'en-US',
      modelName: 'gpt-4',
      audioEncoding: 'mp3',
    });

    expect(tts.generateFinalJson).toHaveBeenCalledWith(
      jasmine.objectContaining({
        prompt: 'test',
        speakers: [maraItem],
        annotatedTurns: [{ speaker: 'Mara', text: '[warm] Hello' }],
        languageCode: 'en-US',
        modelName: 'gpt-4',
        audioEncoding: 'mp3',
      })
    );
    expect(tts.planSingleSpeakerRenderRequests).toHaveBeenCalledWith(finalRequest);
    expect(facade.finalRequest()).toEqual(finalRequest);
    expect(facade.audioProductionPlan()).toEqual(plan);
  });

  it('createAudioProductionPlan calls workflow configureOutput when sessionId exists', async () => {
    const finalRequest = { test: 'final' } as any;
    const plan = { test: 'plan' } as any;
    facade.setCast([maraItem]);
    facade.setAnnotatedTurns([]);
    // Manually set session ID (normally set by analyzeStory)
    (facade as any)._sessionId.set('session-123');
    tts.generateFinalJson.and.resolveTo(finalRequest);
    tts.planSingleSpeakerRenderRequests.and.resolveTo(plan);

    await facade.createAudioProductionPlan({
      prompt: 'test',
      languageCode: 'en-US',
      modelName: 'gpt-4',
      audioEncoding: 'mp3',
    });

    expect(workflow.configureOutput).toHaveBeenCalledWith(
      'session-123',
      'en-US',
      'gpt-4',
      'mp3',
      new Map([['Mara', 'Kore']])
    );
  });

  it('createAudioProductionPlan handles workflow configureOutput failure gracefully', async () => {
    const finalRequest = { test: 'final' } as any;
    const plan = { test: 'plan' } as any;
    facade.setCast([maraItem]);
    (facade as any)._sessionId.set('session-123');
    tts.generateFinalJson.and.resolveTo(finalRequest);
    tts.planSingleSpeakerRenderRequests.and.resolveTo(plan);
    (workflow.configureOutput as jasmine.Spy).and.returnValue({
      toPromise: () => Promise.reject(new Error('Workflow error'))
    });

    // Should not throw, error is caught
    await facade.createAudioProductionPlan({
      prompt: 'test',
      languageCode: 'en-US',
      modelName: 'gpt-4',
      audioEncoding: 'mp3',
    });

    expect(facade.audioProductionPlan()).toEqual(plan);
    expect(facade.error()).toBeNull(); // Error is caught, not exposed
  });

  it('createAudioProductionPlan fails and sets error message', async () => {
    const error = new Error('Generation failed');
    tts.generateFinalJson.and.rejectWith(error);

    await facade.createAudioProductionPlan({
      prompt: 'test',
      languageCode: 'en-US',
      modelName: 'gpt-4',
      audioEncoding: 'mp3',
    });

    expect(facade.error()).toBe('Generation failed');
  });

  // ── analyzeStory with workflow failures ────────────────────────────────────

  it('analyzeStory continues when createWorkflow fails', async () => {
    tts.analyzeSpeakers.and.resolveTo([maraItem]);
    (workflow.createWorkflow as jasmine.Spy).and.returnValue({
      toPromise: () => Promise.reject(new Error('Network error'))
    });

    await facade.analyzeStory('Story text');

    expect(facade.cast()).toEqual([maraItem]);
    expect(facade.error()).toBeNull(); // Error from workflow is swallowed
    expect(facade.sessionId()).toBeNull(); // Session not set
  });

  it('analyzeStory triggers discoverSpeakers background call when createWorkflow succeeds', async () => {
    tts.analyzeSpeakers.and.resolveTo([maraItem]);
    (workflow.createWorkflow as jasmine.Spy).and.returnValue({
      toPromise: () => Promise.resolve({ id: 'session-123' })
    });

    await facade.analyzeStory('Story text');

    expect(facade.sessionId()).toBe('session-123');
    expect(workflow.discoverSpeakers).toHaveBeenCalledWith('session-123');
  });

  it('analyzeStory handles discoverSpeakers background failure', async () => {
    tts.analyzeSpeakers.and.resolveTo([maraItem]);
    (workflow.createWorkflow as jasmine.Spy).and.returnValue({
      toPromise: () => Promise.resolve({ id: 'session-123' })
    });
    (workflow.discoverSpeakers as jasmine.Spy).and.returnValue({
      toPromise: () => Promise.reject(new Error('Backend error'))
    });

    // Should not throw
    await facade.analyzeStory('Story text');

    expect(facade.cast()).toEqual([maraItem]);
  });

  // ── createScriptPreview with workflow ──────────────────────────────────────

  it('createScriptPreview calls workflow splitDialogue when sessionId exists', async () => {
    facade.setCast([maraItem]);
    (facade as any)._sessionId.set('session-123');
    tts.splitDialogue.and.resolveTo([{ speaker: 'Mara', text: 'Hello' }]);

    await facade.createScriptPreview('Mara: Hello');

    expect(workflow.splitDialogue).toHaveBeenCalledWith(
      'session-123',
      jasmine.any(Array)
    );
  });

  // ── createPerformanceNotes with workflow ──────────────────────────────────

  it('createPerformanceNotes calls workflow annotateDialogue when sessionId exists', async () => {
    facade.setScriptTurns([{ speaker: 'Mara', text: 'Hello' }]);
    (facade as any)._sessionId.set('session-123');
    tts.annotateEmotions.and.resolveTo([{ speaker: 'Mara', text: '[warm] Hello' }]);

    await facade.createPerformanceNotes();

    expect(workflow.annotateDialogue).toHaveBeenCalledWith(
      'session-123',
      [{ speaker: 'Mara', text: 'Hello' }]
    );
  });

  // ── saveScriptTurnEdit without annotated turns ─────────────────────────────

  it('saveScriptTurnEdit does not mark notes stale when annotated turns empty', () => {
    facade.setScriptTurns([{ speaker: 'Mara', text: 'We go.' }]);
    facade.setAnnotatedTurns([]);
    facade.startScriptTurnEdit(0);
    const draft = facade.scriptTurnEditDraft()!;
    draft.text = 'We stay.';
    facade.saveScriptTurnEdit(0);

    expect(facade.performanceNotesStale()).toBeFalse();
  });

  // ── saveCastEdit with no draft ────────────────────────────────────────────

  it('saveCastEdit returns early when draft is null', () => {
    facade.setCast([maraItem, jonasItem]);
    facade.startCastEdit(0);
    (facade as any)._castEditDraft.set(null);

    facade.saveCastEdit(0);

    // Cast should not change
    expect(facade.cast()).toEqual([maraItem, jonasItem]);
  });

  // ── saveScriptTurnEdit with no draft ──────────────────────────────────────

  it('saveScriptTurnEdit returns early when draft is null', () => {
    const turn = { speaker: 'Mara', text: 'We go.' };
    facade.setScriptTurns([turn]);
    facade.startScriptTurnEdit(0);
    (facade as any)._scriptTurnEditDraft.set(null);

    facade.saveScriptTurnEdit(0);

    // Script should not change
    expect(facade.scriptTurns()).toEqual([turn]);
  });

  // ── resetPipeline ─────────────────────────────────────────────────────────

  it('resetPipeline clears all state, error, and calls audio reset', () => {
    const onReset = jasmine.createSpy('onAudioReset');
    facade.onAudioReset = onReset;
    facade.setCast([maraItem]);
    facade.setScriptTurns([{ speaker: 'Mara', text: 'Hello' }]);
    facade.setScriptApproved(true);

    facade.resetPipeline();

    expect(facade.cast()).toEqual([]);
    expect(facade.scriptTurns()).toEqual([]);
    expect(facade.scriptApproved()).toBeFalse();
    expect(facade.error()).toBeNull();
    expect(onReset).toHaveBeenCalledTimes(1);
    expect(renderSvc.abortAll).toHaveBeenCalled();
    expect(fullSvc.clearAudio).toHaveBeenCalled();
  });

  // ── Error handling: non-Error exception ────────────────────────────────────

  it('analyzeStory uses fallback message when error is not an Error instance', async () => {
    tts.analyzeSpeakers.and.rejectWith('String error');

    await facade.analyzeStory('Story text');

    expect(facade.error()).toBe('Story analysis failed.');
  });

  it('createScriptPreview uses fallback message for non-Error exceptions', async () => {
    facade.setCast([maraItem]);
    tts.splitDialogue.and.rejectWith({ code: 500 });

    await facade.createScriptPreview('text');

    expect(facade.error()).toBe('Script preview failed.');
  });

  // ── speakerOptions edge cases ─────────────────────────────────────────────

  it('speakerOptions adds draft speaker when editing', () => {
    facade.setCast([maraItem]);
    const draftSpeaker = { speakerName: 'NewSpeaker', roleDescription: '', voiceSuggestion: 'Zephyr' };
    (facade as any)._scriptTurnEditDraft.set({ speaker: 'NewSpeaker', text: 'test' });

    const options = facade.speakerOptions();

    expect(options).toContain('Mara');
    expect(options).toContain('NewSpeaker');
  });

  it('speakerOptions handles case-insensitive Narrator check', () => {
    facade.setCast([{ speakerName: 'NARRATOR', roleDescription: '', voiceSuggestion: 'Zephyr' }]);

    const options = facade.speakerOptions();

    expect(options).toContain('Narrator');
  });

  // ── setCurrentProjectId ───────────────────────────────────────────────────

  it('setCurrentProjectId updates currentProjectId', () => {
    facade.setCurrentProjectId('proj-123');
    expect(facade.currentProjectId()).toBe('proj-123');
  });
});
