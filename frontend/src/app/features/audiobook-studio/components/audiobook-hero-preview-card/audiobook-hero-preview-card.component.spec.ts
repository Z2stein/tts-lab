import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AudiobookHeroPreviewCardComponent } from './audiobook-hero-preview-card.component';

describe('AudiobookHeroPreviewCardComponent', () => {
  let fixture: ComponentFixture<AudiobookHeroPreviewCardComponent>;
  let component: AudiobookHeroPreviewCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AudiobookHeroPreviewCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AudiobookHeroPreviewCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the three preview stages', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Your story');
    expect(text).toContain('AI detects cast');
    expect(text).toContain('Audiobook preview');
  });

  it('renders an avatar for each cast member', () => {
    component.cast = [
      { name: 'Narrator', initials: 'N', tone: 'warm calm', imageUrl: '/a.png' },
      { name: 'Mara', initials: 'M', tone: 'young tense', imageUrl: '/b.png' },
    ];
    fixture.detectChanges();
    const avatars = fixture.nativeElement.querySelectorAll('img');
    expect(avatars.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('2 characters ready');
  });

  it('falls back to initials when a cast member has no image', () => {
    component.cast = [{ name: 'Station Keeper', initials: 'SK', tone: 'old gravelly' }];
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SK');
  });

  it('emits togglePlay when the preview play button is clicked', () => {
    const spy = jasmine.createSpy('togglePlay');
    component.togglePlay.subscribe(spy);
    const btn = fixture.nativeElement.querySelector('[aria-label="Play audiobook preview"]') as HTMLButtonElement;
    btn.click();
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('shows a pause icon and label when playing', () => {
    component.playing = true;
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[aria-label="Pause audiobook preview"]') as HTMLButtonElement;
    expect(btn).not.toBeNull();
    expect(btn.querySelector('.play-icon-pause')).not.toBeNull();
  });

  it('marks waveform bars active in proportion to progress', () => {
    const total = component.waveformBars.length;
    component.progress = 0;
    expect(component.barActive(0)).toBe(false);

    component.progress = 0.5;
    expect(component.barActive(Math.round(total * 0.5) - 1)).toBe(true);
    expect(component.barActive(total - 1)).toBe(false);

    component.progress = 1;
    expect(component.barActive(total - 1)).toBe(true);
  });
});
