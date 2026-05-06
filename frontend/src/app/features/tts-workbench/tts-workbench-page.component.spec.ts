import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TtsWorkbenchPageComponent } from './tts-workbench-page.component';
import { TtsWorkbenchService } from './tts-workbench.service';

describe('TtsWorkbenchPageComponent', () => {
  let fixture: ComponentFixture<TtsWorkbenchPageComponent>;
  let component: TtsWorkbenchPageComponent;
  let ttsWorkbenchService: jasmine.SpyObj<TtsWorkbenchService>;

  beforeEach(async () => {
    ttsWorkbenchService = jasmine.createSpyObj<TtsWorkbenchService>('TtsWorkbenchService', [
      'analyzeSpeakers',
      'splitDialogue',
      'annotateEmotions',
      'generateFinalJson',
      'planProviderCompatibleRequests'
    ]);

    await TestBed.configureTestingModule({
      imports: [TtsWorkbenchPageComponent],
      providers: [{ provide: TtsWorkbenchService, useValue: ttsWorkbenchService }]
    }).compileComponents();

    fixture = TestBed.createComponent(TtsWorkbenchPageComponent);
    component = fixture.componentInstance;
  });

  it('submits raw dialogue and displays speaker voice suggestions', async () => {
    ttsWorkbenchService.analyzeSpeakers.and.resolveTo([
      { speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm neutral voice' }
    ]);
    component.rawDialogueControl.setValue('Alice: Hello');

    await component.analyzeSpeakers();
    fixture.detectChanges();

    expect(ttsWorkbenchService.analyzeSpeakers).toHaveBeenCalledWith('Alice: Hello');
    expect(fixture.nativeElement.textContent).toContain('Speaker & Voice Suggestions');
    expect(fixture.nativeElement.querySelector('input[aria-label="Speaker name"]').value).toBe('Alice');
  });

  it('displays split turns after splitting dialogue', async () => {
    component.speakers = [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm neutral voice' }];
    component.rawDialogueControl.setValue('Alice: Hello');
    ttsWorkbenchService.splitDialogue.and.resolveTo([{ speaker: 'Alice', text: 'Hello' }]);

    await component.splitDialogue();
    fixture.detectChanges();

    expect(ttsWorkbenchService.splitDialogue).toHaveBeenCalledWith('Alice: Hello', component.speakers);
    expect(fixture.nativeElement.textContent).toContain('Speaker Split Preview');
    expect(fixture.nativeElement.querySelector('textarea[aria-label="Turn text"]').value).toBe('Hello');
  });

  it('displays annotated turns after emotion annotation', async () => {
    component.speakerTurns = [{ speaker: 'Alice', text: 'Hello!' }];
    ttsWorkbenchService.annotateEmotions.and.resolveTo([{ speaker: 'Alice', text: '[urgent] Hello!' }]);

    await component.annotateEmotions();
    fixture.detectChanges();

    expect(ttsWorkbenchService.annotateEmotions).toHaveBeenCalledWith(component.speakerTurns);
    expect(fixture.nativeElement.querySelector('textarea[aria-label="Annotated turn text"]').value).toBe('[urgent] Hello!');
  });

  it('displays formatted final request JSON', async () => {
    component.speakers = [{ speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm neutral voice' }];
    component.annotatedTurns = [{ speaker: 'Alice', text: '[urgent] Hello!' }];
    ttsWorkbenchService.generateFinalJson.and.resolveTo({
      input: { prompt: 'Prompt' },
      voice: { languageCode: 'en-US' },
      audioConfig: { audioEncoding: 'MP3' }
    });

    await component.generateFinalJson();
    fixture.detectChanges();

    expect(ttsWorkbenchService.generateFinalJson).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('"audioEncoding": "MP3"');
  });


  it('displays provider-compatible request plan chunks', async () => {
    component.finalRequest = {
      input: { prompt: 'Prompt' },
      voice: { languageCode: 'en-US' },
      audioConfig: { audioEncoding: 'MP3' }
    };
    ttsWorkbenchService.planProviderCompatibleRequests.and.resolveTo({
      chunks: [{
        chunkNumber: 1,
        speakers: ['Alice'],
        request: component.finalRequest
      }]
    });

    await component.planProviderCompatibleRequests();
    fixture.detectChanges();

    expect(ttsWorkbenchService.planProviderCompatibleRequests).toHaveBeenCalledWith(component.finalRequest);
    expect(fixture.nativeElement.textContent).toContain('Provider-Compatible Request Splitting Preview');
    expect(fixture.nativeElement.textContent).toContain('Chunk count: 1');
    expect(fixture.nativeElement.textContent).toContain('Included speakers: Alice');
  });

  it('keeps editable preview fields in component state', () => {
    component.speakers = [
      { speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm neutral voice' }
    ];
    component.speakerTurns = [{ speaker: 'Alice', text: 'Hello' }];
    component.annotatedTurns = [{ speaker: 'Alice', text: '[calm] Hello' }];
    fixture.detectChanges();

    const speakerNameInput = fixture.nativeElement.querySelector('input[aria-label="Speaker name"]') as HTMLInputElement;
    speakerNameInput.value = 'Narrator';
    speakerNameInput.dispatchEvent(new Event('input'));

    const turnTextInput = fixture.nativeElement.querySelector('textarea[aria-label="Turn text"]') as HTMLTextAreaElement;
    turnTextInput.value = 'Updated split text';
    turnTextInput.dispatchEvent(new Event('input'));

    const annotatedTextInput = fixture.nativeElement.querySelector('textarea[aria-label="Annotated turn text"]') as HTMLTextAreaElement;
    annotatedTextInput.value = '[urgent] Updated annotated text';
    annotatedTextInput.dispatchEvent(new Event('input'));

    expect(component.speakers[0].speakerName).toBe('Narrator');
    expect(component.speakerTurns[0].text).toBe('Updated split text');
    expect(component.annotatedTurns[0].text).toBe('[urgent] Updated annotated text');
  });

  it('shows an error when analysis fails', async () => {
    ttsWorkbenchService.analyzeSpeakers.and.rejectWith(new Error('Speaker voice analysis failed (HTTP 500).'));

    await component.analyzeSpeakers();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Speaker voice analysis failed (HTTP 500).');
  });
});
