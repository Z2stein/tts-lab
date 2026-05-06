import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TtsWorkbenchPageComponent } from './tts-workbench-page.component';
import { TtsWorkbenchService } from './tts-workbench.service';

describe('TtsWorkbenchPageComponent', () => {
  let fixture: ComponentFixture<TtsWorkbenchPageComponent>;
  let component: TtsWorkbenchPageComponent;
  let ttsWorkbenchService: jasmine.SpyObj<TtsWorkbenchService>;

  beforeEach(async () => {
    ttsWorkbenchService = jasmine.createSpyObj<TtsWorkbenchService>('TtsWorkbenchService', ['analyzeSpeakers']);

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
    expect(fixture.nativeElement.textContent).toContain('Alice');
    expect(fixture.nativeElement.textContent).toContain('Warm neutral voice');
  });

  it('shows an error when analysis fails', async () => {
    ttsWorkbenchService.analyzeSpeakers.and.rejectWith(new Error('Speaker voice analysis failed (HTTP 500).'));

    await component.analyzeSpeakers();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Speaker voice analysis failed (HTTP 500).');
  });
});
