import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ChatbotService } from '../../../../../chatbot/chatbot.service';
import { ChatResponse } from '../../../../../shared/api-contract.generated';
import { loadTestContractJson } from '../../../../../shared/test-contracts';
import { StoryGeneratorComponent } from './story-generator.component';

describe('StoryGeneratorComponent', () => {
  let fixture: ComponentFixture<StoryGeneratorComponent>;
  let component: StoryGeneratorComponent;
  let chatbotService: jasmine.SpyObj<ChatbotService>;
  let chatResponse: ChatResponse;

  beforeEach(async () => {
    chatResponse = await loadTestContractJson<ChatResponse>('chat/generate-story/response.json');

    chatbotService = jasmine.createSpyObj<ChatbotService>('ChatbotService', ['sendMessage']);

    await TestBed.configureTestingModule({
      imports: [StoryGeneratorComponent],
      providers: [{ provide: ChatbotService, useValue: chatbotService }],
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
    it('calls ChatbotService.sendMessage with a prompt containing the idea text', async () => {
      chatbotService.sendMessage.and.resolveTo(chatResponse);
      component.ideaControl.setValue('A lonely lighthouse keeper');

      await component.generateStory();

      expect(chatbotService.sendMessage).toHaveBeenCalledOnceWith(
        jasmine.stringContaining('A lonely lighthouse keeper'),
        null
      );
    });

    it('includes selected chip instructions in the prompt', async () => {
      chatbotService.sendMessage.and.resolveTo(chatResponse);
      component.ideaControl.setValue('An idea');
      component.toggleChip('Make it dramatic');

      await component.generateStory();

      const [prompt] = chatbotService.sendMessage.calls.mostRecent().args;
      expect(prompt).toContain('dramatic');
    });

    it('does not include unselected chip instructions in the prompt', async () => {
      chatbotService.sendMessage.and.resolveTo(chatResponse);
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      const [prompt] = chatbotService.sendMessage.calls.mostRecent().args;
      expect(prompt).not.toContain('narrator');
    });

    it('emits storyGenerated with the answer on success', async () => {
      chatbotService.sendMessage.and.resolveTo(chatResponse);
      component.ideaControl.setValue('An idea');

      const emitted: string[] = [];
      component.storyGenerated.subscribe(v => emitted.push(v));

      await component.generateStory();

      expect(emitted).toEqual([chatResponse.answer]);
    });

    it('sets error signal and does not emit storyGenerated on failure', async () => {
      chatbotService.sendMessage.and.rejectWith(new Error('API failed'));
      component.ideaControl.setValue('An idea');

      const emitted: string[] = [];
      component.storyGenerated.subscribe(v => emitted.push(v));

      await component.generateStory();

      expect(component.error()).toBe('API failed');
      expect(emitted).toEqual([]);
    });

    it('resets generating to false after success', async () => {
      chatbotService.sendMessage.and.resolveTo(chatResponse);
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      expect(component.generating()).toBeFalse();
    });

    it('resets generating to false after failure', async () => {
      chatbotService.sendMessage.and.rejectWith(new Error('fail'));
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      expect(component.generating()).toBeFalse();
    });

    it('clears error before a new generation attempt', async () => {
      component.error.set('Previous error');
      chatbotService.sendMessage.and.resolveTo(chatResponse);
      component.ideaControl.setValue('An idea');

      await component.generateStory();

      expect(component.error()).toBeNull();
    });

    it('does nothing when idea is empty', async () => {
      await component.generateStory();

      expect(chatbotService.sendMessage).not.toHaveBeenCalled();
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
