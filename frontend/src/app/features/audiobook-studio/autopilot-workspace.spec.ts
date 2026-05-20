import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AudiobookStudioWorkspaceComponent } from './audiobook-studio-page.component';
import { AudiobookApiService } from '../audiobook-shared/service/audiobook-api.service';
import { AudiobookWorkflowService } from '../audiobook-shared/service/audiobook-workflow.service';
import { AudiobookLibraryService } from '../audiobook-library/services/audiobook-library.service';
import { VoicePickerService } from './services/voice-picker.service';

describe('AudiobookStudioWorkspaceComponent — Autopilot', () => {
  let fixture: ComponentFixture<AudiobookStudioWorkspaceComponent>;
  let component: AudiobookStudioWorkspaceComponent;
  let workflow: jasmine.SpyObj<AudiobookWorkflowService>;
  let api: jasmine.SpyObj<AudiobookApiService>;
  let library: jasmine.SpyObj<AudiobookLibraryService>;
  let voicePicker: jasmine.SpyObj<VoicePickerService>;

  const speakers = [{ speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'ZEPHYR' }];

  function snapshot(stage: string, extra: Record<string, unknown> = {}) {
    return {
      projectId: 'project-1',
      title: 'The Hidden Signal',
      sourceLanguageCode: 'en-US',
      storyText: 'Mara: We go now and find the hidden signal together.',
      workflowStage: stage,
      speakers,
      scriptTurns: [{ speaker: 'Mara', text: 'We go now.' }],
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
    } as never;
  }

  beforeEach(async () => {
    workflow = jasmine.createSpyObj<AudiobookWorkflowService>('AudiobookWorkflowService', [
      'analyzeSpeakers', 'approveCast', 'saveCast', 'splitDialogue', 'saveScriptPreview',
      'annotateEmotions', 'saveProductionSettings', 'approveScript', 'generateFinalJson',
      'planSingleSpeakerRenderRequests', 'createAudio', 'markAudioGenerated'
    ]);
    api = jasmine.createSpyObj<AudiobookApiService>('AudiobookApiService', [
      'createAudio', 'createAudioForRenderRequest', 'post', 'postJsonResponse', 'postBlobResponse'
    ]);
    library = jasmine.createSpyObj<AudiobookLibraryService>('AudiobookLibraryService', ['updateTitle']);
    voicePicker = jasmine.createSpyObj<VoicePickerService>('VoicePickerService', ['getVoiceCatalog']);
    voicePicker.getVoiceCatalog.and.resolveTo([]);

    workflow.analyzeSpeakers.and.resolveTo({
      speakers, projectId: 'project-1', projectTitle: 'The Hidden Signal',
      sourceLanguageCode: 'en-US', productionLanguageCode: 'en-US'
    });
    workflow.approveCast.and.resolveTo(snapshot('CAST_APPROVED'));
    workflow.splitDialogue.and.resolveTo([{ speaker: 'Mara', text: 'We go now.' }]);
    workflow.approveScript.and.resolveTo(snapshot('SCRIPT_APPROVED'));
    workflow.annotateEmotions.and.resolveTo(snapshot('PERFORMANCE_READY', {
      annotatedTurns: [{ speaker: 'Mara', text: '[urgent] We go now.' }]
    }));
    workflow.saveProductionSettings.and.resolveTo(snapshot('PERFORMANCE_READY'));
    workflow.generateFinalJson.and.resolveTo({ request: { input: [] } } as never);
    workflow.planSingleSpeakerRenderRequests.and.resolveTo({
      renderRequests: [{
        input: { text: 'We go now.', segmentOrderIndex: 0 },
        voice: { languageCode: 'en-US', speakerName: 'Mara', name: 'Zephyr', modelName: 'm' },
        audioConfig: { audioEncoding: 'MP3' }
      }]
    } as never);
    workflow.markAudioGenerated.and.resolveTo(snapshot('AUDIO_GENERATED'));
    api.createAudioForRenderRequest.and.resolveTo({
      blob: new Blob(['mp3'], { type: 'audio/mpeg' }),
      filename: 'tts-render-request-1.mp3'
    });

    await TestBed.configureTestingModule({
      imports: [AudiobookStudioWorkspaceComponent],
      providers: [
        { provide: AudiobookWorkflowService, useValue: workflow },
        { provide: AudiobookApiService, useValue: api },
        { provide: AudiobookLibraryService, useValue: library },
        { provide: VoicePickerService, useValue: voicePicker },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookStudioWorkspaceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function byTestId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  }

  it('defaults to the Autopilot view and toggles the guided workflow in and out', () => {
    expect(component.studioMode()).toBe('autopilot');
    expect(byTestId('autopilot-setup')).not.toBeNull();
    expect(byTestId('story-section')).toBeNull();

    component.setStudioMode('guided');
    fixture.detectChanges();
    expect(byTestId('story-section')).not.toBeNull();
    expect(byTestId('autopilot-setup')).toBeNull();
  });

  it('honours an initialMode input (resume page opens guided)', () => {
    component.initialMode = 'guided';
    fixture.detectChanges();
    expect(component.studioMode()).toBe('guided');
    expect(byTestId('story-section')).not.toBeNull();
  });

  it('opens the matching guided section when a progress step is clicked', () => {
    component.openGuidedStep('split-script');
    fixture.detectChanges();
    expect(component.studioMode()).toBe('guided');
    expect(byTestId('script-section')).not.toBeNull();
  });

  it('runs the full workflow automatically and produces a playable preview', async () => {
    component.setStudioMode('autopilot');
    component.storyTextControl.setValue('Mara: We go now and find the hidden signal together.');
    fixture.detectChanges();

    await component.startAutopilot();
    fixture.detectChanges();

    const statuses = component.autopilotSteps.reduce<Record<string, string>>((acc, s) => {
      acc[s.id] = s.status;
      return acc;
    }, {});
    expect(statuses['analyze-story']).toBe('completed');
    expect(statuses['assign-voices']).toBe('completed');
    expect(statuses['split-script']).toBe('completed');
    expect(statuses['generate-preview']).toBe('completed');
    expect(component.autopilotFinished).toBeTrue();
    expect(component.fullPlanAudioUrl).not.toBeNull();
    expect(workflow.analyzeSpeakers).toHaveBeenCalled();
    expect(workflow.approveScript).toHaveBeenCalled();
  });

  it('does not navigate away (no projectCreated emit) while Autopilot runs', async () => {
    const emitted: string[] = [];
    component.projectCreated.subscribe((id) => emitted.push(id));
    component.setStudioMode('autopilot');
    component.storyTextControl.setValue('Mara: We go now and find the hidden signal together.');

    await component.startAutopilot();

    expect(emitted).toEqual([]);
  });

  it('stops on a failed step and falls back to the guided workflow with data preserved', async () => {
    workflow.approveCast.and.rejectWith(new Error('Voice assignment failed.'));
    component.setStudioMode('autopilot');
    component.storyTextControl.setValue('Mara: We go now and find the hidden signal together.');

    await component.startAutopilot();
    fixture.detectChanges();

    const assignVoices = component.autopilotSteps.find((s) => s.id === 'assign-voices');
    expect(assignVoices?.status).toBe('failed');
    expect(assignVoices?.errorMessage).toBe('Voice assignment failed.');
    expect(component.autopilotFinished).toBeFalse();
    expect(byTestId('autopilot-error')).not.toBeNull();

    component.openInGuidedWorkflow();
    fixture.detectChanges();

    expect(component.studioMode()).toBe('guided');
    // Analyze succeeded before the failure — its cast result is still available.
    expect(component.cast.length).toBe(1);
    expect(byTestId('cast-section')).not.toBeNull();
  });
});
