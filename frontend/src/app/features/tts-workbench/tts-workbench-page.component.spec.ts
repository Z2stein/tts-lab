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
      'planSingleSpeakerRenderRequests',
      'createAudio',
      'createAudioForRenderRequest'
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


  it('displays single-speaker render plan requests', async () => {
    component.finalRequest = {
      input: { prompt: 'Prompt' },
      voice: { languageCode: 'en-US' },
      audioConfig: { audioEncoding: 'MP3' }
    };
    ttsWorkbenchService.planSingleSpeakerRenderRequests.and.resolveTo({
      renderRequests: [{
        input: { text: 'Hello' },
        voice: { languageCode: 'en-US', name: 'Kore', modelName: '{{google-model}}' },
        audioConfig: { audioEncoding: 'MP3' }
      }]
    });

    await component.planSingleSpeakerRenderRequests();
    fixture.detectChanges();

    expect(ttsWorkbenchService.planSingleSpeakerRenderRequests).toHaveBeenCalledWith(component.finalRequest);
    expect(fixture.nativeElement.textContent).toContain('Single-Speaker Render Plan Preview');
    expect(fixture.nativeElement.textContent).toContain('Render request count: 1');
    expect(fixture.nativeElement.textContent).toContain('Render request 1');
    expect(fixture.nativeElement.textContent).toContain('\"name\": \"Kore\"');
  });

  it('creates audio from the render plan and starts a blob download', async () => {
    component.singleSpeakerRenderPlan = {
      renderRequests: [{
        input: { text: 'Hello' },
        voice: { languageCode: 'en-US', name: 'Kore', modelName: '{{google-model}}' },
        audioConfig: { audioEncoding: 'MP3' }
      }]
    };
    const blob = new Blob(['mp3'], { type: 'audio/mpeg' });
    ttsWorkbenchService.createAudio.and.resolveTo({ blob, filename: 'tts-render-request-1.mp3' });
    const clickSpy = jasmine.createSpy('click');
    const anchor = document.createElement('a');
    spyOn(anchor, 'click').and.callFake(clickSpy);
    spyOn(document, 'createElement').and.returnValue(anchor);
    spyOn(window.URL, 'createObjectURL').and.returnValue('blob:test-url');
    spyOn(window.URL, 'revokeObjectURL');

    await component.createAudio();

    expect(ttsWorkbenchService.createAudio).toHaveBeenCalledWith(component.singleSpeakerRenderPlan);
    expect(anchor.download).toBe('tts-render-request-1.mp3');
    expect(anchor.href).toContain('blob:test-url');
    expect(clickSpy).toHaveBeenCalled();
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:test-url');
  });



  it('creates audio for an individual render request with an independent download filename', async () => {
    const renderRequest = {
      input: { text: 'Second' },
      voice: { languageCode: 'en-US', name: 'Kore', modelName: '{{google-model}}' },
      audioConfig: { audioEncoding: 'MP3' }
    };
    component.singleSpeakerRenderPlan = {
      renderRequests: [{ input: { text: 'First' }, voice: {}, audioConfig: {} }, renderRequest]
    };
    const blob = new Blob(['mp3'], { type: 'audio/mpeg' });
    ttsWorkbenchService.createAudioForRenderRequest.and.resolveTo({ blob, filename: 'ignored-backend-single-name.mp3' });
    const clickSpy = jasmine.createSpy('click');
    const anchor = document.createElement('a');
    spyOn(anchor, 'click').and.callFake(clickSpy);
    spyOn(document, 'createElement').and.returnValue(anchor);
    spyOn(window.URL, 'createObjectURL').and.returnValue('blob:request-url');
    spyOn(window.URL, 'revokeObjectURL');

    await component.createAudioForRenderRequest(renderRequest, 1);

    expect(ttsWorkbenchService.createAudioForRenderRequest).toHaveBeenCalledWith(renderRequest);
    expect(anchor.download).toBe('tts-render-request-2.mp3');
    expect(clickSpy).toHaveBeenCalled();
  });

  it('shows per-request audio errors separately from full-plan audio errors', async () => {
    const renderRequest = { input: { text: 'Hello' }, voice: {}, audioConfig: {} };
    component.singleSpeakerRenderPlan = { renderRequests: [renderRequest] };
    ttsWorkbenchService.createAudioForRenderRequest.and.rejectWith(new Error('Request audio failed.'));

    await component.createAudioForRenderRequest(renderRequest, 0);
    fixture.detectChanges();

    expect(component.renderRequestAudioState(0).error).toBe('Request audio failed.');
    expect(component.fullPlanAudioError).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Request audio failed.');
  });

  it('shows an audio creation error when backend audio creation fails', async () => {
    component.singleSpeakerRenderPlan = {
      renderRequests: [{ input: { text: 'Hello' }, voice: {}, audioConfig: {} }]
    };
    ttsWorkbenchService.createAudio.and.rejectWith(new Error('The text-to-speech provider is currently unavailable. Please try again later.'));

    await component.createAudio();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('The text-to-speech provider is currently unavailable. Please try again later.');
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
