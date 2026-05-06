import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TtsWorkbenchPageComponent } from './tts-workbench-page.component';
import { TtsWorkbenchService } from './tts-workbench.service';

describe('TtsWorkbenchPageComponent', () => {
  let fixture: ComponentFixture<TtsWorkbenchPageComponent>;
  let component: TtsWorkbenchPageComponent;
  let ttsWorkbenchService: jasmine.SpyObj<TtsWorkbenchService>;

  beforeEach(async () => {
    ttsWorkbenchService = jasmine.createSpyObj('TtsWorkbenchService', ['analyzeSpeakerVoices']);
    await TestBed.configureTestingModule({
      imports: [TtsWorkbenchPageComponent],
      providers: [{ provide: TtsWorkbenchService, useValue: ttsWorkbenchService }]
    }).compileComponents();

    fixture = TestBed.createComponent(TtsWorkbenchPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders raw dialogue input', () => {
    expect(fixture.nativeElement.querySelector('textarea')).not.toBeNull();
  });

  it('shows speaker results after analysis', async () => {
    ttsWorkbenchService.analyzeSpeakerVoices.and.resolveTo([
      { speakerName: 'Alice', roleDescription: 'Narrator', voiceSuggestion: 'Warm voice' }
    ]);
    component.rawDialogueControl.setValue('Alice: Hello');

    await component.analyze();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Alice');
    expect(fixture.nativeElement.textContent).toContain('Warm voice');
  });

  it('shows empty state when no speakers are returned', async () => {
    ttsWorkbenchService.analyzeSpeakerVoices.and.resolveTo([]);

    await component.analyze();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No speakers found.');
  });

  it('shows error state when analysis fails', async () => {
    ttsWorkbenchService.analyzeSpeakerVoices.and.rejectWith(new Error('fail'));

    await component.analyze();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Speaker voice analysis failed.');
  });
});
