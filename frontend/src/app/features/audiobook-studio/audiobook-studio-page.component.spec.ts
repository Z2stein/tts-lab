import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AudiobookStudioPageComponent } from './audiobook-studio-page.component';
import { TtsWorkbenchService } from '../tts-workbench/tts-workbench.service';

describe('AudiobookStudioPageComponent', () => {
  let fixture: ComponentFixture<AudiobookStudioPageComponent>;
  let component: AudiobookStudioPageComponent;
  let ttsWorkbenchService: jasmine.SpyObj<TtsWorkbenchService>;

  beforeEach(async () => {
    ttsWorkbenchService = jasmine.createSpyObj<TtsWorkbenchService>('TtsWorkbenchService', [
      'analyzeSpeakers',
      'splitDialogue',
      'annotateEmotions',
      'generateFinalJson',
      'planSingleSpeakerRenderRequests',
      'createAudio',
      'createAudioForRenderRequest'
    ]);

    await TestBed.configureTestingModule({
      imports: [AudiobookStudioPageComponent],
      providers: [{ provide: TtsWorkbenchService, useValue: ttsWorkbenchService }]
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookStudioPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('fills the textarea when the sample story is selected', () => {
    fixture.nativeElement.querySelector('button.secondary-button').click();
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('#story-text') as HTMLTextAreaElement;
    expect(textarea.value).toContain('Mara');
    expect(textarea.value).toContain('Jonas');
    expect(textarea.value).toContain('Station Keeper');
  });

  it('shows cast cards after story analysis succeeds', async () => {
    ttsWorkbenchService.analyzeSpeakers.and.resolveTo([
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
    ]);
    component.storyTextControl.setValue('Mara: We go now.');

    await component.analyzeStory();
    fixture.detectChanges();

    expect(ttsWorkbenchService.analyzeSpeakers).toHaveBeenCalledWith('Mara: We go now.');
    expect(fixture.nativeElement.textContent).toContain('Mara');
    expect(fixture.nativeElement.textContent).toContain('Detected character');
    expect(fixture.nativeElement.textContent).toContain('Warm alto voice');
  });

  it('shows script preview turns after cast analysis continues', async () => {
    component.storyTextControl.setValue('Mara: We go now.\nJonas: Together.');
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
      { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
    ];
    ttsWorkbenchService.splitDialogue.and.resolveTo([
      { speaker: 'Mara', text: 'We go now.' },
      { speaker: 'Jonas', text: 'Together.' }
    ]);

    await component.createScriptPreview();
    fixture.detectChanges();

    expect(ttsWorkbenchService.splitDialogue).toHaveBeenCalledWith(component.storyTextControl.value, component.cast);
    expect(fixture.nativeElement.textContent).toContain('Review script preview');
    expect(fixture.nativeElement.textContent).toContain('We go now.');
    expect(fixture.nativeElement.textContent).toContain('Together.');
  });

  it('shows a user-facing backend error when analysis fails', async () => {
    ttsWorkbenchService.analyzeSpeakers.and.rejectWith(new Error('The cast analysis provider is currently unavailable. Please try again later.'));
    component.storyTextControl.setValue('Mara: Hello');

    await component.analyzeStory();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('The cast analysis provider is currently unavailable. Please try again later.');
  });
});
