import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CastCardComponent } from './cast-card.component';
import { WaveSurferService } from '../../../services/wave-surfer.service';
import { VoiceSampleService } from '../../../services/voice-sample.service';
import { SpeakerVoiceAnalysisItem } from '../../../../audiobook-shared/service/audiobook-workflow.service';
import { normalizeVoiceAssetName } from '../../../utils/speaker-name';
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { WaveformPlayerComponent } from '../../waveform-player/waveform-player.component';

const SPEAKER_STUB: SpeakerVoiceAnalysisItem = {
  speakerName: 'Mara',
  roleDescription: 'Young woman. Brave, curious and quick to act.',
  voiceSuggestion: 'PUCK',
};

const NO_VOICE_STUB: SpeakerVoiceAnalysisItem = {
  speakerName: 'Unknown',
  roleDescription: 'A mysterious character.',
  voiceSuggestion: '',
};

const noOpStyleFn = () => ({});

// Minimal stub so tests don't need WaveSurfer canvas
@Component({
  selector: 'app-waveform-player',
  standalone: true,
  template: '',
})
class WaveformPlayerStub {
  @Input() key = '';
  @Input() src: string | null = null;
  @Input() playing = false;
  @Input() ariaLabel = '';
  @Input() progressColor: string | null = null;
  @Input() waveHeight: number | null = null;
  @Input() showPlayButton = true;
  @Input() showTime = true;
  @Output() playingChange = new EventEmitter<boolean>();
}

function makeWaveSurferSpy(): jasmine.SpyObj<WaveSurferService> {
  return jasmine.createSpyObj<WaveSurferService>('WaveSurferService', ['get', 'create', 'destroy', 'pauseAll']);
}

describe('normalizeVoiceAssetName', () => {
  it('lowercases an uppercase voice name', () => {
    expect(normalizeVoiceAssetName('ACHERNAR')).toBe('achernar');
  });

  it('trims surrounding whitespace', () => {
    expect(normalizeVoiceAssetName('  puck  ')).toBe('puck');
  });

  it('returns empty string for an empty input', () => {
    expect(normalizeVoiceAssetName('')).toBe('');
  });

  it('handles null-like values gracefully via empty string fallback', () => {
    expect(normalizeVoiceAssetName(null as unknown as string)).toBe('');
  });
});

describe('CastCardComponent', () => {
  let fixture: ComponentFixture<CastCardComponent>;
  let component: CastCardComponent;
  let waveSurferService: jasmine.SpyObj<WaveSurferService>;

  function setup(speaker: SpeakerVoiceAnalysisItem = SPEAKER_STUB): void {
    waveSurferService = makeWaveSurferSpy();
    const voiceSampleStub = jasmine.createSpyObj<VoiceSampleService>('VoiceSampleService', ['play', 'pause', 'destroy']);

    TestBed.configureTestingModule({
      imports: [CastCardComponent, WaveformPlayerStub],
      providers: [
        { provide: WaveSurferService, useValue: waveSurferService },
        { provide: VoiceSampleService, useValue: voiceSampleStub },
      ],
    })
    .overrideComponent(CastCardComponent, {
      remove: { imports: [WaveformPlayerComponent] },
      add: { imports: [WaveformPlayerStub] },
    });

    fixture = TestBed.createComponent(CastCardComponent);
    component = fixture.componentInstance;
    component.speaker = speaker;
    component.index = 0;
    component.accentClass = 'cast-accent-0';
    component.speakerStyleFn = noOpStyleFn;
    fixture.detectChanges();
  }

  describe('voice name normalisation', () => {
    it('exposes a lowercase voiceName from an uppercase voiceSuggestion', () => {
      setup();
      expect(component.voiceName).toBe('puck');
    });

    it('returns empty string when voiceSuggestion is empty', () => {
      setup(NO_VOICE_STUB);
      expect(component.voiceName).toBe('');
    });
  });

  describe('avatar path', () => {
    it('builds the avatar path from the normalised voice name', () => {
      setup();
      expect(component.voiceAvatarPath).toBe('/assets/voices/puck/avatar.png');
    });

    it('returns empty string when there is no voice', () => {
      setup(NO_VOICE_STUB);
      expect(component.voiceAvatarPath).toBe('');
    });

    it('renders the avatar img element with the correct src', () => {
      setup();
      const img = fixture.debugElement.query(By.css('.voice-avatar-img'));
      expect(img).toBeTruthy();
      expect(img.nativeElement.getAttribute('src')).toBe('/assets/voices/puck/avatar.png');
    });

    it('falls back to initials when avatarError is true', () => {
      setup();
      component.avatarError = true;
      fixture.detectChanges();
      const img = fixture.debugElement.query(By.css('.voice-avatar-img'));
      const span = fixture.debugElement.query(By.css('.cast-avatar'));
      expect(img.nativeElement.hidden).toBeTrue();
      expect(span.nativeElement.hidden).toBeFalse();
    });
  });

  describe('demo MP3 path', () => {
    it('builds the demo path from the normalised voice name', () => {
      setup();
      expect(component.voiceDemoPath).toBe('/assets/voices/puck/demo.mp3');
    });

    it('returns empty string when there is no voice', () => {
      setup(NO_VOICE_STUB);
      expect(component.voiceDemoPath).toBe('');
    });
  });

  describe('fallback state (no voice)', () => {
    it('disables the preview button', () => {
      setup(NO_VOICE_STUB);
      const btn = fixture.debugElement.query(By.css('.preview-voice-button'));
      expect(btn.nativeElement.disabled).toBeTrue();
    });

    it('shows a "No voice selected yet" label', () => {
      setup(NO_VOICE_STUB);
      const voiceDisplay = fixture.debugElement.query(By.css('.voice-name-display'));
      expect(voiceDisplay.nativeElement.textContent.trim()).toBe('No voice selected yet');
    });

    it('still shows the change voice button', () => {
      setup(NO_VOICE_STUB);
      const btn = fixture.debugElement.query(By.css('[data-testid="cast-change-voice-0"]'));
      expect(btn).toBeTruthy();
    });
  });

  describe('Preview voice button', () => {
    it('calls waveSurferService.get with the demo key and then playPause', () => {
      setup();
      const mockWs = jasmine.createSpyObj('WaveSurfer', ['playPause']);
      mockWs.playPause.and.returnValue(Promise.resolve());
      waveSurferService.get.and.returnValue(mockWs);

      const btn = fixture.debugElement.query(By.css('.preview-voice-button'));
      btn.nativeElement.click();

      expect(waveSurferService.get).toHaveBeenCalledWith('voice-demo:puck');
      expect(mockWs.playPause).toHaveBeenCalledTimes(1);
    });

    it('does not throw when no WaveSurfer instance exists yet', () => {
      setup();
      waveSurferService.get.and.returnValue(null);
      expect(() => component.togglePreview()).not.toThrow();
    });
  });

  describe('Change voice button', () => {
    it('emits changeVoice when clicked', () => {
      setup();
      const emitSpy = spyOn(component.changeVoice, 'emit');
      const btn = fixture.debugElement.query(By.css('[data-testid="cast-change-voice-0"]'));
      btn.nativeElement.click();
      expect(emitSpy).toHaveBeenCalledOnceWith();
    });
  });

  describe('Remove speaker', () => {
    it('shows a confirmation prompt before removing', () => {
      setup();
      const emitSpy = spyOn(component.removeSpeaker, 'emit');

      fixture.debugElement.query(By.css('[data-testid="cast-remove-0"]')).nativeElement.click();
      fixture.detectChanges();

      const panel = fixture.debugElement.query(By.css('[data-testid="cast-remove-confirm-panel-0"]'));
      expect(panel).toBeTruthy();
      expect(panel.nativeElement.textContent).toContain('Remove this speaker from the cast?');
      expect(emitSpy).not.toHaveBeenCalled();
    });

    it('emits removeSpeaker only after the confirmation is accepted', () => {
      setup();
      const emitSpy = spyOn(component.removeSpeaker, 'emit');

      fixture.debugElement.query(By.css('[data-testid="cast-remove-0"]')).nativeElement.click();
      fixture.detectChanges();
      fixture.debugElement.query(By.css('[data-testid="cast-remove-confirm-0"]')).nativeElement.click();

      expect(emitSpy).toHaveBeenCalledOnceWith();
    });

    it('cancels the removal without emitting', () => {
      setup();
      const emitSpy = spyOn(component.removeSpeaker, 'emit');

      fixture.debugElement.query(By.css('[data-testid="cast-remove-0"]')).nativeElement.click();
      fixture.detectChanges();
      fixture.debugElement.query(By.css('[data-testid="cast-remove-cancel-0"]')).nativeElement.click();
      fixture.detectChanges();

      expect(emitSpy).not.toHaveBeenCalled();
      expect(fixture.debugElement.query(By.css('[data-testid="cast-remove-confirm-panel-0"]'))).toBeNull();
    });

    it('blocks removing the only speaker and shows a message instead of confirming', () => {
      setup();
      component.isOnlySpeaker = true;
      fixture.detectChanges();
      const emitSpy = spyOn(component.removeSpeaker, 'emit');

      fixture.debugElement.query(By.css('[data-testid="cast-remove-0"]')).nativeElement.click();
      fixture.detectChanges();

      const blocked = fixture.debugElement.query(By.css('[data-testid="cast-remove-blocked-0"]'));
      expect(blocked.nativeElement.textContent).toContain('At least one speaker is required.');
      expect(fixture.debugElement.query(By.css('[data-testid="cast-remove-confirm-panel-0"]'))).toBeNull();
      expect(emitSpy).not.toHaveBeenCalled();
    });
  });

  describe('accent colour', () => {
    it('maps cast-accent-0 to the amber colour', () => {
      setup();
      expect(component.accentColor).toBe('#f0ad5d');
    });

    it('maps cast-accent-1 to the purple colour', () => {
      setup();
      component.accentClass = 'cast-accent-1';
      expect(component.accentColor).toBe('#c965ff');
    });
  });
});
