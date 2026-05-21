import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DemoAudioPreviewComponent } from './demo-audio-preview.component';

describe('DemoAudioPreviewComponent', () => {
  let fixture: ComponentFixture<DemoAudioPreviewComponent>;
  let component: DemoAudioPreviewComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DemoAudioPreviewComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(DemoAudioPreviewComponent);
    component = fixture.componentInstance;
    component.audioSrc = '/assets/audio/voice-samples/full-text-preview.mp3';
    component.waveformBars = [18, 30, 42, 54];
    fixture.detectChanges();
  });

  it('renders waveform bars across a full-width preview track', () => {
    const waveform = fixture.nativeElement.querySelector('[data-testid="cv-demo-waveform"]') as HTMLElement | null;
    const bars = waveform?.querySelectorAll('span');

    expect(waveform).not.toBeNull();
    expect(waveform?.classList).toContain('w-full');
    expect(waveform?.classList).toContain('justify-between');
    expect(bars?.length).toBe(4);
  });
});
