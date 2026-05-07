import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AudiobookStudioPageComponent, formatSpeakerDisplayName } from './audiobook-studio-page.component';
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
    expect(fixture.nativeElement.textContent).toContain('Cast needs review');
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
    component.cast = [
      { speakerName: 'StationKeeper', roleDescription: 'Old role', voiceSuggestion: 'Old voice' }
    ];
    fixture.detectChanges();

    clickButton('Edit');
    fixture.detectChanges();
    setInputValue('#cast-speaker-name-0', 'Station Keeper');
    setInputValue('#cast-role-description-0', 'Caretaker of the midnight platform');
    setInputValue('#cast-voice-suggestion-0', 'Warm gravelly voice');
    clickButton('Save');
    fixture.detectChanges();

    ttsWorkbenchService.splitDialogue.and.resolveTo([
      { speaker: 'Station Keeper', text: 'All aboard.' }
    ]);

    await component.createScriptPreview();

    expect(ttsWorkbenchService.splitDialogue).toHaveBeenCalledWith(component.storyTextControl.value, [
      {
        speakerName: 'Station Keeper',
        roleDescription: 'Caretaker of the midnight platform',
        voiceSuggestion: 'Warm gravelly voice'
      }
    ]);
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
    expect(fixture.nativeElement.textContent).toContain('Script needs review');
  });

  it('shows an edited script turn after saving the speaker and text', async () => {
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
      { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
    ];
    component.scriptTurns = [{ speaker: 'Mara', text: 'We go now.' }];
    fixture.detectChanges();

    clickButton('Edit', 2);
    fixture.detectChanges();

    setSelectValue('#script-speaker-0', 'Jonas');
    setInputValue('#script-text-0', 'We wait for the signal.');
    clickButton('Save');
    fixture.detectChanges();

    expect(component.scriptTurns[0]).toEqual({ speaker: 'Jonas', text: 'We wait for the signal.' });
    expect(fixture.nativeElement.textContent).toContain('Jonas');
    expect(fixture.nativeElement.textContent).toContain('We wait for the signal.');
  });

  it('disables the production plan button when script edits make performance notes stale', async () => {
    component.cast = [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' }
    ];
    component.scriptTurns = [{ speaker: 'Mara', text: 'We go now.' }];
    component.scriptApproved = true;
    component.annotatedTurns = [{ speaker: 'Mara', text: '[urgent] We go now.' }];
    fixture.detectChanges();

    clickButton('Edit', 1);
    fixture.detectChanges();
    setInputValue('#script-text-0', 'We go at sunrise.');
    clickButton('Save');
    fixture.detectChanges();

    const planButton = buttonByText('Create audio production plan');
    expect(fixture.nativeElement.textContent).toContain('Script changed. Regenerate performance notes before creating the audio production plan.');
    expect(planButton.disabled).toBeTrue();

    clickButton('Approve script');
    ttsWorkbenchService.annotateEmotions.and.resolveTo([{ speaker: 'Mara', text: '[hopeful] We go at sunrise.' }]);
    await component.createPerformanceNotes();
    fixture.detectChanges();

    expect(component.performanceNotesStale).toBeFalse();
    expect(buttonByText('Create audio production plan').disabled).toBeFalse();
  });

  it('continues the emotion annotation flow after the user approves the script', async () => {
    component.scriptTurns = [{ speaker: 'Narrator', text: 'The lamps dimmed.' }];
    ttsWorkbenchService.annotateEmotions.and.resolveTo([{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }]);
    fixture.detectChanges();

    expect(buttonByText('Add performance notes').disabled).toBeTrue();

    clickButton('Approve script');
    fixture.detectChanges();
    clickButton('Add performance notes');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ttsWorkbenchService.annotateEmotions).toHaveBeenCalledWith(component.scriptTurns);
    expect(component.annotatedTurns).toEqual([{ speaker: 'Narrator', text: '[quiet] The lamps dimmed.' }]);
  });

  it('shows a user-facing backend error when analysis fails', async () => {
    ttsWorkbenchService.analyzeSpeakers.and.rejectWith(new Error('The cast analysis provider is currently unavailable. Please try again later.'));
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
});
