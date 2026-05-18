import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AudiobookWorkflowService, GenerateStoryDraftResponse } from '../../../../../features/audiobook-shared/service/audiobook-workflow.service';
import { loadTestContractJson } from '../../../../../shared/test-contracts';
import { StoryGeneratorComponent } from './story-generator.component';

describe('StoryGeneratorComponent', () => {
  let fixture: ComponentFixture<StoryGeneratorComponent>;
  let component: StoryGeneratorComponent;
  let workflowService: jasmine.SpyObj<AudiobookWorkflowService>;
  let draftResponse: GenerateStoryDraftResponse;

  beforeEach(async () => {
    draftResponse = await loadTestContractJson<GenerateStoryDraftResponse>(
      'audiobook-workflow/generate-story-draft/default/response.json'
    );

    workflowService = jasmine.createSpyObj<AudiobookWorkflowService>('AudiobookWorkflowService', ['generateStoryDraft']);

    await TestBed.configureTestingModule({
      imports: [StoryGeneratorComponent],
      providers: [{ provide: AudiobookWorkflowService, useValue: workflowService }],
    }).compileComponents();

    fixture = TestBed.createComponent(StoryGeneratorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('chip toggling', () => {
    it('selects a chip when toggled once', () => {
      component.toggleChip('Add narrator');
      expect(component.selectedChips().has('Add narrator')).toBeTrue();
    });

    it('deselects a chip when toggled twice', () => {
      component.toggleChip('Add narrator');
      component.toggleChip('Add narrator');
      expect(component.selectedChips().has('Add narrator')).toBeFalse();
    });

    it('toggling one chip does not affect another', () => {
      component.toggleChip('Add narrator');
      expect(component.selectedChips().has('Make it dramatic')).toBeFalse();
    });
  });

  describe('canGenerate', () => {
    it('is false when idea input is empty', () => {
      expect(component.canGenerate).toBeFalse();
    });

    it('is true when idea input has text', () => {
      component.ideaControl.setValue('A lonely keeper');
      expect(component.canGenerate).toBeTrue();
    });

    it('is false while generating, even with text', () => {
      component.ideaControl.setValue('some idea');
      component.generating.set(true);
      expect(component.canGenerate).toBeFalse();
    });

    it('is false for whitespace-only input', () => {
      component.ideaControl.setValue('   ');
      expect(component.canGenerate).toBeFalse();
    });
  });

  describe('generateStory()', () => {
    it('calls AudiobookWorkflowService.generateStoryDraft with the idea and selected enhancements', async () => {
      workflowService.generateStoryDraft.and.resolveTo(draftResponse);
      component.ideaControl.setValue('A lonely lighthouse keeper');
      component.toggleChip('Make it dramatic');

      await component.generateStory();

      expect(workflowService.generateStoryDraft).toHaveBeenCalledOnceWith({
        idea: 'A lonely lighthouse keeper',
        enhancements: ['Make it dramatic']
      });
    });

    it('emits storyGenerated with storyDraft on success', async () => {
      workflowService.generateStoryDraft.and.resolveTo(draftResponse);
      component.ideaControl.setValue('An idea');

      const emitted: string[] = [];
      component.storyGenerated.subscribe(v => emitted.push(v));

      await component.generateStory();

      expect(emitted).toEqual([draftResponse.storyDraft]);
    });

    it('sets error signal and does not emit storyGenerated on failure', async () => {
      workflowService.generateStoryDraft.and.rejectWith(new Error('API failed'));
      component.ideaControl.setValue('An idea');

      const emitted: string[] = [];
      component.storyGenerated.subscribe(v => emitted.push(v));

      await component.generateStory();

      expect(component.error()).toBe('API failed');
      expect(emitted).toEqual([]);
    });

    it('resets generating to false after success', async () => {
      workflowService.generateStoryDraft.and.resolveTo(draftResponse);
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      expect(component.generating()).toBeFalse();
    });

    it('resets generating to false after failure', async () => {
      workflowService.generateStoryDraft.and.rejectWith(new Error('fail'));
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      expect(component.generating()).toBeFalse();
    });

    it('clears error before a new generation attempt', async () => {
      component.error.set('Previous error');
      workflowService.generateStoryDraft.and.resolveTo(draftResponse);
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      expect(component.error()).toBeNull();
    });

    it('does nothing when idea is empty', async () => {
      await component.generateStory();

      expect(workflowService.generateStoryDraft).not.toHaveBeenCalled();
    });
  });

  describe('disabled input', () => {
    it('disables the create-story-draft button when disabled=true', () => {
      component.disabled = true;
      component.ideaControl.setValue('some idea');
      fixture.detectChanges();

      const btn = fixture.debugElement.query(By.css('[data-testid="create-story-draft"]'));
      expect(btn.nativeElement.disabled).toBeTrue();
    });

    it('enables the create-story-draft button when disabled=false and idea is non-empty', () => {
      component.disabled = false;
      component.ideaControl.setValue('some idea');
      fixture.detectChanges();

      const btn = fixture.debugElement.query(By.css('[data-testid="create-story-draft"]'));
      expect(btn.nativeElement.disabled).toBeFalse();
    });
  });
});
