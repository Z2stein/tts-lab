import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { VoicePickerModalComponent } from './voice-picker-modal.component';
import { VoicePickerService } from '../../services/voice-picker.service';
import { SpeakerVoiceCatalogItem } from '../../../../shared/api-contract.generated';

const ZEPHYR: SpeakerVoiceCatalogItem = {
  id: 'zephyr',
  providerVoiceName: 'Zephyr',
  displayName: 'Zephyr',
  description: 'Bright and airy.',
  imageUrl: '/assets/voices/zephyr/avatar.png',
  demoMp3Url: '/assets/voices/zephyr/demo.mp3',
};

const PUCK: SpeakerVoiceCatalogItem = {
  id: 'puck',
  providerVoiceName: 'Puck',
  displayName: 'Puck',
  description: 'Playful and quick.',
  imageUrl: '/assets/voices/puck/avatar.png',
  demoMp3Url: '/assets/voices/puck/demo.mp3',
};

describe('VoicePickerModalComponent', () => {
  let fixture: ComponentFixture<VoicePickerModalComponent>;
  let component: VoicePickerModalComponent;
  let voicePickerService: jasmine.SpyObj<VoicePickerService>;

  beforeEach(async () => {
    voicePickerService = jasmine.createSpyObj<VoicePickerService>('VoicePickerService', ['getVoiceCatalog']);
    voicePickerService.getVoiceCatalog.and.resolveTo([ZEPHYR, PUCK]);

    await TestBed.configureTestingModule({
      imports: [VoicePickerModalComponent],
      providers: [{ provide: VoicePickerService, useValue: voicePickerService }],
    }).compileComponents();

    fixture = TestBed.createComponent(VoicePickerModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('is not visible when open is false', () => {
    component.open = false;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="voice-picker-modal"]')).toBeNull();
  });

  it('loads and renders voices from the catalog when opened', fakeAsync(() => {
    component.currentVoiceId = 'zephyr';
    component.speakerName = 'Narrator';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="voice-option-card"]');
    expect(cards.length).toBe(2);
  }));

  it('marks the current voice as selected', fakeAsync(() => {
    component.currentVoiceId = 'zephyr';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    fixture.detectChanges();

    const badges = fixture.nativeElement.querySelectorAll('[data-testid="voice-selected-badge"]');
    expect(badges.length).toBe(1);
    const cards = fixture.nativeElement.querySelectorAll('[data-testid="voice-option-card"]');
    expect(cards[0].querySelector('[data-testid="voice-selected-badge"]')).not.toBeNull();
    expect(cards[1].querySelector('[data-testid="voice-selected-badge"]')).toBeNull();
  }));

  it('filters voices by search query', fakeAsync(() => {
    component.currentVoiceId = 'zephyr';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    component.searchQuery = 'puck';
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="voice-option-card"]');
    expect(cards.length).toBe(1);
    expect(cards[0].getAttribute('data-voice-id')).toBe('puck');
  }));

  it('updates pendingVoiceId when Use voice is clicked', fakeAsync(() => {
    component.currentVoiceId = 'zephyr';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('[data-testid="voice-use-button"]');
    buttons[1].click();
    fixture.detectChanges();

    expect(component.pendingVoiceId).toBe('puck');
    const badges = fixture.nativeElement.querySelectorAll('[data-testid="voice-selected-badge"]');
    expect(badges.length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="voice-option-card"]')[1]
      .querySelector('[data-testid="voice-selected-badge"]')).not.toBeNull();
  }));

  it('emits the selected voice on Done', fakeAsync(() => {
    component.currentVoiceId = 'zephyr';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    fixture.detectChanges();

    const selected: SpeakerVoiceCatalogItem[] = [];
    component.voiceSelected.subscribe((v) => selected.push(v));

    const buttons = fixture.nativeElement.querySelectorAll('[data-testid="voice-use-button"]');
    buttons[1].click();
    fixture.nativeElement.querySelector('[data-testid="voice-picker-done"]').click();
    fixture.detectChanges();

    expect(selected.length).toBe(1);
    expect(selected[0].id).toBe('puck');
  }));

  it('emits closed on Cancel', fakeAsync(() => {
    component.currentVoiceId = 'zephyr';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    fixture.detectChanges();

    let closedCount = 0;
    component.closed.subscribe(() => closedCount++);
    fixture.nativeElement.querySelector('[data-testid="voice-picker-cancel"]').click();
    expect(closedCount).toBe(1);
  }));

  it('only allows one demo to play at a time', fakeAsync(() => {
    const mockAudio = { play: jasmine.createSpy('play').and.returnValue(Promise.resolve()), pause: jasmine.createSpy('pause'), src: '', onended: null as (() => void) | null, onerror: null as (() => void) | null };
    spyOn(window, 'Audio' as never).and.returnValue(mockAudio as never);

    component.currentVoiceId = 'zephyr';
    component.open = true;
    component.ngOnChanges({ open: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } });
    tick();
    fixture.detectChanges();

    component.toggleDemo(ZEPHYR);
    expect(component.isDemoPlaying(ZEPHYR)).toBeTrue();
    expect(component.isDemoPlaying(PUCK)).toBeFalse();

    component.toggleDemo(PUCK);
    expect(component.isDemoPlaying(ZEPHYR)).toBeFalse();
    expect(component.isDemoPlaying(PUCK)).toBeTrue();

    component['stopDemo']();
  }));
});
