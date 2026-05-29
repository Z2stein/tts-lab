import { ComponentFixture, TestBed } from '@angular/core/testing';
import WaveSurfer from 'wavesurfer.js';
import { FocusMonitor } from '@angular/cdk/a11y';
import { VoiceSampleService } from '../../services/voice-sample.service';
import { WaveSurferService } from '../../services/wave-surfer.service';
import { StudioHeroComponent } from './studio-hero.component';

describe('StudioHeroComponent', () => {
  let fixture: ComponentFixture<StudioHeroComponent>;
  let component: StudioHeroComponent;
  let waveSurferService: jasmine.SpyObj<WaveSurferService>;
  let focusMonitor: jasmine.SpyObj<FocusMonitor>;

  beforeEach(async () => {
    waveSurferService = jasmine.createSpyObj<WaveSurferService>('WaveSurferService', [
      'create', 'get', 'destroy', 'bind', 'pauseAll',
    ]);
    focusMonitor = jasmine.createSpyObj<FocusMonitor>('FocusMonitor', ['monitor', 'stopMonitoring']);
    waveSurferService.get.and.returnValue(null);
    const fakeWaveSurfer = jasmine.createSpyObj<WaveSurfer>('WaveSurfer', ['on', 'getDuration', 'playPause']);
    waveSurferService.create.and.returnValue(fakeWaveSurfer);

    await TestBed.configureTestingModule({
      imports: [StudioHeroComponent],
      providers: [
        { provide: WaveSurferService, useValue: waveSurferService },
        { provide: VoiceSampleService, useValue: jasmine.createSpyObj('VoiceSampleService', ['pause']) },
        { provide: FocusMonitor, useValue: focusMonitor },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StudioHeroComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the hero heading', () => {
    expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();
  });

  it('renders icon badges with svg artwork for the story and cast cards', () => {
    const cards = Array.from(fixture.nativeElement.querySelectorAll('article')) as HTMLElement[];
    const storyCard = cards.find((card) => card.textContent?.includes('Your story'));
    const castCard = cards.find((card) => card.textContent?.includes('Detected cast'));

    expect(storyCard?.querySelector('svg')).not.toBeNull();
    expect(castCard?.querySelector('svg')).not.toBeNull();
  });

  it('renders cast rows for each hero cast member', () => {
    component.heroCast = [
      { name: 'Mara', initials: 'M', tone: 'Warm alto' },
      { name: 'Jonas', initials: 'J', tone: 'Gentle tenor' },
    ];
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mara');
    expect(fixture.nativeElement.textContent).toContain('Jonas');
  });

  it('emits focusStoryInput when the CTA is clicked', () => {
    const spy = jasmine.createSpy('focusStoryInput');
    component.focusStoryInput.subscribe(spy);
    const cta = fixture.nativeElement.querySelector('.primary-button') as HTMLButtonElement;
    cta.click();
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('emits playVoiceSample with member name when a cast button is clicked', () => {
    component.heroCast = [{ name: 'Mara', initials: 'M', tone: 'Warm' }];
    fixture.detectChanges();
    const spy = jasmine.createSpy('playVoiceSample');
    component.playVoiceSample.subscribe(spy);
    const castBtn = fixture.nativeElement.querySelector('[aria-label="Preview voice for Mara"]') as HTMLButtonElement;
    castBtn.click();
    expect(spy).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'Mara' }));
  });

  it('starts FocusMonitor on the CTA button in ngAfterViewInit', () => {
    expect(focusMonitor.monitor).toHaveBeenCalled();
  });

  it('stops FocusMonitor on ngOnDestroy', () => {
    component.ngOnDestroy();
    expect(focusMonitor.stopMonitoring).toHaveBeenCalled();
    expect(waveSurferService.destroy).toHaveBeenCalledWith('demo');
  });

  it('toggles is-playing class on the demo button when demoPlaying changes', () => {
    component.demoPlaying = true;
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.secondary-button') as HTMLButtonElement;
    expect(btn.classList).toContain('is-playing');
  });

  it('reflects activeSampleKey as is-playing on the matching cast button', () => {
    component.heroCast = [{ name: 'Mara', initials: 'M', tone: 'Warm' }];
    component.activeSampleKey = 'voice:Mara';
    fixture.detectChanges();
    const castBtn = fixture.nativeElement.querySelector('[aria-label="Pause voice for Mara"]') as HTMLButtonElement;
    expect(castBtn.classList).toContain('is-playing');
  });
});
