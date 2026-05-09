import { TestBed } from '@angular/core/testing';
import { TtsWorkbenchService } from '../tts-workbench/tts-workbench.service';
import { AudiobookStudioFacade } from './audiobook-studio.facade';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from './services/render-request-audio.service';

describe('AudiobookStudioFacade', () => {
  let facade: AudiobookStudioFacade;
  let tts: jasmine.SpyObj<TtsWorkbenchService>;
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
    renderSvc = jasmine.createSpyObj<RenderRequestAudioService>('RenderRequestAudioService', ['abortAll', 'revokeUrls']);
    fullSvc = jasmine.createSpyObj<FullAudioGenerationService>('FullAudioGenerationService', ['clearAudio']);

    TestBed.configureTestingModule({
      providers: [
        AudiobookStudioFacade,
        { provide: TtsWorkbenchService, useValue: tts },
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
});
