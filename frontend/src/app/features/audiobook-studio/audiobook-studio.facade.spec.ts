import { TestBed } from '@angular/core/testing';
import { AudiobookLibraryService } from '../audiobook-library/services/audiobook-library.service';
import { AudiobookWorkflowService } from '../audiobook-shared/service/audiobook-workflow.service';
import { AudiobookStudioFacade } from './audiobook-studio.facade';
import { FullAudioGenerationService } from './services/full-audio-generation.service';
import { RenderRequestAudioService } from '../audiobook-shared/service/render-request-audio.service';

describe('AudiobookStudioFacade', () => {
  let facade: AudiobookStudioFacade;
  let workflow: jasmine.SpyObj<AudiobookWorkflowService>;
  let library: jasmine.SpyObj<AudiobookLibraryService>;
  let renderSvc: jasmine.SpyObj<RenderRequestAudioService>;
  let fullSvc: jasmine.SpyObj<FullAudioGenerationService>;

  const maraItem = { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Kore' };
  const jonasItem = { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Iapetus' };

  beforeEach(() => {
    workflow = jasmine.createSpyObj<AudiobookWorkflowService>('AudiobookWorkflowService', [
      'analyzeSpeakers', 'approveCast', 'splitDialogue', 'annotateEmotions',
      'saveCast', 'saveScriptPreview', 'saveProductionSettings', 'approveScript',
      'generateFinalJson', 'planSingleSpeakerRenderRequests',
      'markAudioGenerated',
    ]);
    library = jasmine.createSpyObj<AudiobookLibraryService>('AudiobookLibraryService', ['updateTitle']);
    renderSvc = jasmine.createSpyObj<RenderRequestAudioService>('RenderRequestAudioService', ['abortAll', 'revokeUrls']);
    fullSvc = jasmine.createSpyObj<FullAudioGenerationService>('FullAudioGenerationService', ['cancel', 'clearAudio']);

    TestBed.configureTestingModule({
      providers: [
        AudiobookStudioFacade,
        { provide: AudiobookWorkflowService, useValue: workflow },
        { provide: AudiobookLibraryService, useValue: library },
        { provide: RenderRequestAudioService, useValue: renderSvc },
        { provide: FullAudioGenerationService, useValue: fullSvc },
      ],
    });

    facade = TestBed.inject(AudiobookStudioFacade);
  });

  describe('analyzeStory', () => {
    it('sets loadingAction, calls AudiobookWorkflowService, updates cast, and clears error on success', async () => {
      workflow.analyzeSpeakers.and.resolveTo({ speakers: [maraItem], projectId: 'project-1', projectTitle: 'The Hidden Signal' });

      const promise = facade.analyzeStory('story text');
      expect(facade.loadingAction()).toBe('cast');

      await promise;

      expect(workflow.analyzeSpeakers).toHaveBeenCalledWith('story text');
      expect(facade.cast()).toEqual([maraItem]);
      expect(facade.currentProjectId()).toBe('project-1');
      expect(facade.projectTitle()).toBe('The Hidden Signal');
      expect(facade.loadingAction()).toBeNull();
      expect(facade.error()).toBeNull();
    });

    it('sets error and clears loadingAction on failure', async () => {
      workflow.analyzeSpeakers.and.rejectWith(new Error('Network Error'));

      await facade.analyzeStory('story text');

      expect(facade.error()).toBe('Network Error');
      expect(facade.loadingAction()).toBeNull();
      expect(facade.cast()).toEqual([]);
    });

    it('resets downstream pipeline state when successful', async () => {
      facade.setCast([jonasItem]);
      facade.setScriptTurns([{ speaker: 'Jonas', text: 'Hi' }]);
      facade.setAnnotatedTurns([{ speaker: 'Jonas', text: 'Hi' }]);
      facade.setFinalRequest({ input: {}, voice: {}, audioConfig: {} });
      facade.setAudioProductionPlan({ renderRequests: [] });
      facade.setCastReviewed(true);
      facade.setScriptApproved(true);
      facade.setPerformanceNotesStale(false);

      workflow.analyzeSpeakers.and.resolveTo({ speakers: [maraItem], projectId: 'project-1', projectTitle: 'The Hidden Signal' });
      await facade.analyzeStory('new story');

      expect(facade.cast()).toEqual([maraItem]);
      expect(facade.currentProjectId()).toBe('project-1');
      expect(facade.projectTitle()).toBe('The Hidden Signal');
      expect(facade.scriptTurns()).toEqual([]);
      expect(facade.annotatedTurns()).toEqual([]);
      expect(facade.finalRequest()).toBeNull();
      expect(facade.audioProductionPlan()).toBeNull();
      expect(facade.castReviewed()).toBeFalse();
      expect(facade.scriptApproved()).toBeFalse();
      expect(facade.performanceNotesStale()).toBeFalse();
      expect(fullSvc.clearAudio).toHaveBeenCalled();
      expect(renderSvc.abortAll).toHaveBeenCalled();
      expect(renderSvc.revokeUrls).toHaveBeenCalled();
    });
  });

  describe('createScriptPreview', () => {
    it('sets loadingAction, calls AudiobookWorkflowService, updates scriptTurns on success', async () => {
      facade.setCast([maraItem]);
      facade.setCurrentProjectId('project-1');
      facade.setCastReviewed(true);
      workflow.splitDialogue.and.resolveTo([{ speaker: 'Mara', text: 'Hello' }]);

      const promise = facade.createScriptPreview('story text');
      expect(facade.loadingAction()).toBe('script');

      await promise;

      expect(workflow.splitDialogue).toHaveBeenCalledWith('story text', [maraItem], 'project-1');
      expect(facade.scriptTurns()).toEqual([{ speaker: 'Mara', text: 'Hello' }]);
      expect(facade.loadingAction()).toBeNull();
      expect(facade.error()).toBeNull();
    });

    it('sets error and clears loadingAction on failure', async () => {
      facade.setCast([maraItem]);
      facade.setCurrentProjectId('project-1');
      facade.setCastReviewed(true);
      workflow.splitDialogue.and.rejectWith(new Error('API Failure'));
      await facade.createScriptPreview('story text');

      expect(facade.error()).toBe('API Failure');
      expect(facade.loadingAction()).toBeNull();
    });
  });

  describe('approveCast', () => {
    it('persists the cast approval snapshot and marks the cast as reviewed', async () => {
      facade.setCurrentProjectId('project-1');
      workflow.approveCast.and.resolveTo({
        projectId: 'project-1',
        title: 'The Hidden Signal',
        storyText: 'Mara: Hello',
        workflowStage: 'CAST_APPROVED',
        speakers: [maraItem],
        scriptTurns: [],
        annotatedTurns: [],
        productionSettings: {
          prompt: 'Prompt',
          languageCode: 'en-US',
          modelName: 'gemini-3.1-flash-tts-preview',
          audioEncoding: 'MP3'
        },
        performanceNotesStale: false
      } as never);

      await facade.approveCast();

      expect(workflow.approveCast).toHaveBeenCalledWith('project-1');
      expect(facade.castReviewed()).toBeTrue();
    });
  });

  describe('hydrateFromSnapshot', () => {
    it('hydrates stale performance notes from the backend snapshot', () => {
      facade.hydrateFromSnapshot({
        projectId: 'project-1',
        title: 'The Hidden Signal',
        storyText: 'Mara: Hello',
        workflowStage: 'SCRIPT_APPROVED',
        speakers: [maraItem],
        scriptTurns: [{ speaker: 'Mara', text: 'Hello' }],
        annotatedTurns: [{ speaker: 'Mara', text: '[calm] Hello' }],
        productionSettings: {
          prompt: 'Prompt',
          languageCode: 'en-US',
          modelName: 'gemini-3.1-flash-tts-preview',
          audioEncoding: 'MP3'
        },
        performanceNotesStale: true
      } as never);

      expect(facade.currentProjectId()).toBe('project-1');
      expect(facade.performanceNotesStale()).toBeTrue();
      expect(facade.scriptApproved()).toBeTrue();
      expect(facade.castReviewed()).toBeTrue();
    });
  });

  describe('createPerformanceNotes', () => {
    it('updates annotatedTurns and clears stale flag on success', async () => {
      facade.setScriptTurns([{ speaker: 'Mara', text: 'Hello' }]);
      facade.setCurrentProjectId('project-1');
      workflow.annotateEmotions.and.resolveTo({
        projectId: 'project-1',
        title: 'The Hidden Signal',
        storyText: 'Mara: Hello',
        workflowStage: 'PERFORMANCE_READY',
        speakers: [maraItem],
        scriptTurns: [{ speaker: 'Mara', text: 'Hello' }],
        annotatedTurns: [{ speaker: 'Mara', text: '<speak>Hello</speak>' }],
        productionSettings: {
          prompt: 'Prompt',
          languageCode: 'en-US',
          modelName: 'gemini-3.1-flash-tts-preview',
          audioEncoding: 'MP3'
        },
        performanceNotesStale: false
      } as never);
      facade.setPerformanceNotesStale(true);

      await facade.createPerformanceNotes();

      expect(workflow.annotateEmotions).toHaveBeenCalledWith('project-1');
      expect(facade.annotatedTurns()).toEqual([{ speaker: 'Mara', text: '<speak>Hello</speak>' }]);
      expect(facade.performanceNotesStale()).toBeFalse();
      expect(facade.performanceReady()).toBeTrue();
    });

    it('blocks annotation while a script edit is still open', async () => {
      facade.setScriptTurns([{ speaker: 'Mara', text: 'Hello' }]);
      facade.setCurrentProjectId('project-1');
      facade.startScriptTurnEdit(0);

      await facade.createPerformanceNotes();

      expect(facade.error()).toBe('Save or cancel the script edit before adding emotion and pacing.');
      expect(workflow.annotateEmotions).not.toHaveBeenCalled();
    });

    it('sets an error when the project id is missing', async () => {
      facade.setScriptTurns([{ speaker: 'Mara', text: 'Hello' }]);

      await facade.createPerformanceNotes();

      expect(facade.error()).toBe('Story analysis did not return a project id.');
      expect(workflow.annotateEmotions).not.toHaveBeenCalled();
    });
  });

  describe('createAudioProductionPlan', () => {
    it('generates final JSON then plan, and updates both signals', async () => {
      const requestParams = {
        prompt: 'A test',
        languageCode: 'en-US',
        modelName: 'test-model',
        audioEncoding: 'MP3'
      };

      facade.setCast([maraItem]);
      facade.setAnnotatedTurns([{ speaker: 'Mara', text: '<speak>Hi</speak>' }]);
      facade.setCurrentProjectId('project-1');

      const finalReq = { input: { text: 'test' }, voice: {}, audioConfig: {} };
      const plan = { renderRequests: [] };

      workflow.saveProductionSettings.and.resolveTo({
        projectId: 'project-1',
        title: 'Generated audiobook',
        storyText: null,
        workflowStage: 'CAST_REVIEW',
        speakers: [maraItem],
        scriptTurns: [],
        annotatedTurns: [],
        productionSettings: requestParams,
        performanceNotesStale: false
      } as never);
      workflow.generateFinalJson.and.resolveTo(finalReq);
      workflow.planSingleSpeakerRenderRequests.and.resolveTo(plan);

      await facade.createAudioProductionPlan(requestParams);

      expect(workflow.saveProductionSettings).toHaveBeenCalledWith('project-1', requestParams);
      expect(workflow.generateFinalJson).toHaveBeenCalledWith({
        ...requestParams,
        speakers: [maraItem],
        annotatedTurns: [{ speaker: 'Mara', text: '<speak>Hi</speak>' }]
      });
      expect(workflow.planSingleSpeakerRenderRequests).toHaveBeenCalledWith(finalReq);

      expect(facade.finalRequest()).toBe(finalReq);
      expect(facade.audioProductionPlan()).toBe(plan);
    });
  });

  describe('finalizeAudioGeneration', () => {
    it('marks the preview current and refreshes the workflow snapshot', async () => {
      facade.setCurrentProjectId('project-1');
      workflow.markAudioGenerated.and.resolveTo({
        projectId: 'project-1',
        title: 'The Hidden Signal',
        storyText: 'Mara: Hello',
        workflowStage: 'AUDIO_GENERATED',
        speakers: [maraItem],
        scriptTurns: [{ speaker: 'Mara', text: 'Hello' }],
        annotatedTurns: [{ speaker: 'Mara', text: '<speak>Hello</speak>' }],
        productionSettings: {
          prompt: 'Prompt',
          languageCode: 'en-US',
          modelName: 'gemini-3.1-flash-tts-preview',
          audioEncoding: 'MP3'
        },
        performanceNotesStale: false
      } as never);

      await facade.finalizeAudioGeneration('project-1');

      expect(workflow.markAudioGenerated).toHaveBeenCalledWith('project-1');
      expect(facade.workflowStage()).toBe('AUDIO_GENERATED');
      expect(facade.loadingAction()).toBeNull();
      expect(facade.error()).toBeNull();
    });
  });

  describe('scriptGroups', () => {
    it('groups sequential turns by the same speaker', () => {
      facade.setScriptTurns([
        { speaker: 'Mara', text: 'One' },
        { speaker: 'Mara', text: 'Two' },
        { speaker: 'Jonas', text: 'Three' },
        { speaker: 'Mara', text: 'Four' }
      ]);

      const groups = facade.scriptGroups();
      expect(groups.length).toBe(3);

      expect(groups[0].speaker).toBe('Mara');
      expect(groups[0].turns.map((t) => t.index)).toEqual([0, 1]);

      expect(groups[1].speaker).toBe('Jonas');
      expect(groups[1].turns.map((t) => t.index)).toEqual([2]);

      expect(groups[2].speaker).toBe('Mara');
      expect(groups[2].turns.map((t) => t.index)).toEqual([3]);
    });
  });

  describe('approveScript', () => {
    it('sets scriptApproved true and performanceNotesStale false', async () => {
      facade.setCurrentProjectId('project-1');
      workflow.approveScript.and.resolveTo({
        projectId: 'project-1',
        title: 'The Hidden Signal',
        storyText: 'Mara: Hello',
        workflowStage: 'SCRIPT_APPROVED',
        speakers: [maraItem],
        scriptTurns: [{ speaker: 'Mara', text: 'Hello' }],
        annotatedTurns: [],
        productionSettings: {
          prompt: 'Prompt',
          languageCode: 'en-US',
          modelName: 'gemini-3.1-flash-tts-preview',
          audioEncoding: 'MP3'
        },
        performanceNotesStale: false
      } as never);

      await facade.approveScript();
      expect(facade.scriptApproved()).toBeTrue();
      expect(facade.performanceNotesStale()).toBeFalse();
    });
  });

  describe('saveProjectTitle', () => {
    it('persists the edited title and updates local state', async () => {
      facade.setCurrentProjectId('project-1');
      library.updateTitle.and.resolveTo({
        id: 'project-1',
        title: 'New Title',
        status: 'NEEDS_REVIEW',
        speechSegmentCount: 0,
        speakerCount: null,
        totalDurationSeconds: null,
        createdAt: '2026-05-12T10:00:00Z',
        updatedAt: '2026-05-12T10:01:00Z',
        speechSegments: [],
        audioAssets: []
      } as never);

      const promise = facade.saveProjectTitle('New Title');
      expect(facade.loadingAction()).toBe('title');
      await promise;

      expect(library.updateTitle).toHaveBeenCalledWith('project-1', 'New Title');
      expect(facade.projectTitle()).toBe('New Title');
      expect(facade.loadingAction()).toBeNull();
      expect(facade.error()).toBeNull();
    });
  });

  describe('saveCastEdit', () => {
    it('persists cast changes and hydrates from the backend snapshot', async () => {
      facade.setCast([maraItem, jonasItem]);
      facade.setCurrentProjectId('project-1');
      facade.setCastReviewed(true);
      facade.startCastEdit(0);

      const draft = facade.castEditDraft()!;
      draft.speakerName = 'Mara Updated';
      workflow.saveCast.and.resolveTo({
        projectId: 'project-1',
        title: 'The Hidden Signal',
        storyText: 'Mara: Hello',
        workflowStage: 'CAST_REVIEW',
        speakers: [
          { speakerName: 'Mara Updated', roleDescription: 'Bold traveler', voiceSuggestion: 'Kore' },
          jonasItem
        ],
        scriptTurns: [],
        annotatedTurns: [],
        audioAssets: [],
        productionSettings: {
          prompt: 'Prompt',
          languageCode: 'en-US',
          modelName: 'gemini-3.1-flash-tts-preview',
          audioEncoding: 'MP3'
        },
        audioAssetsCurrent: false,
        performanceNotesStale: false
      } as never);

      const promise = facade.saveCastEdit(0);
      expect(facade.loadingAction()).toBe('cast-edit');
      await promise;

      expect(workflow.saveCast).toHaveBeenCalledWith('project-1', {
        speakers: [
          { speakerName: 'Mara Updated', roleDescription: 'Bold traveler', voiceSuggestion: 'Kore' },
          jonasItem
        ]
      });
      expect(facade.cast()[0].speakerName).toBe('Mara Updated');
      expect(facade.editingCastIndex()).toBeNull();
      expect(facade.castEditDraft()).toBeNull();
      expect(facade.castReviewed()).toBeFalse();
      expect(renderSvc.abortAll).toHaveBeenCalled();
    });
  });

  describe('saveScriptTurnEdit', () => {
    it('persists edited turns before closing the editor and resets downstream pipeline', async () => {
      facade.setScriptTurns([
        { speaker: 'Mara', text: 'Hello' },
        { speaker: 'Jonas', text: 'Hi' }
      ]);
      facade.setAnnotatedTurns([
        { speaker: 'Mara', text: 'Hello' },
        { speaker: 'Jonas', text: 'Hi' }
      ]);
      facade.setCurrentProjectId('project-1');
      facade.startScriptTurnEdit(1);

      const draft = facade.scriptTurnEditDraft()!;
      draft.text = 'Greetings';
      workflow.saveScriptPreview.and.resolveTo([
        { speaker: 'Mara', text: 'Hello' },
        { speaker: 'Jonas', text: 'Greetings' }
      ]);

      const promise = facade.saveScriptTurnEdit(1);
      expect(facade.loadingAction()).toBe('script-edit');
      await promise;

      expect(facade.scriptTurns()[1].text).toBe('Greetings');
      expect(facade.editingScriptTurnIndex()).toBeNull();
      expect(facade.scriptTurnEditDraft()).toBeNull();
      expect(workflow.saveScriptPreview).toHaveBeenCalledWith('project-1', [
        { speaker: 'Mara', text: 'Hello' },
        { speaker: 'Jonas', text: 'Greetings' }
      ]);

      expect(facade.scriptApproved()).toBeFalse();
      expect(facade.performanceNotesStale()).toBeTrue();
      expect(renderSvc.abortAll).toHaveBeenCalled();
    });
  });
});
