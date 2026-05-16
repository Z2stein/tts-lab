import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpeakerVoiceAnalysisItem } from '../../../../audiobook-shared/service/audiobook-workflow.service';
import { formatSpeakerDisplayName, normalizeVoiceAssetName, speakerInitials } from '../../../utils/speaker-name';
import { WaveSurferService } from '../../../services/wave-surfer.service';
import { WaveformPlayerComponent } from '../../waveform-player/waveform-player.component';

const ACCENT_COLORS: Record<string, string> = {
  'cast-accent-0': '#f0ad5d',
  'cast-accent-1': '#c965ff',
  'cast-accent-2': '#48b5ff',
  'cast-accent-3': '#8fe77a',
  'cast-accent-4': '#ff7da8',
  'cast-accent-5': '#7de7d4',
};

@Component({
  selector: 'app-cast-card',
  standalone: true,
  imports: [CommonModule, FormsModule, WaveformPlayerComponent],
  templateUrl: './cast-card.component.html'
})
export class CastCardComponent {
  @Input() speaker!: SpeakerVoiceAnalysisItem;
  @Input() index!: number;
  @Input() accentClass = '';
  @Input() editing = false;
  @Input() editDraft: SpeakerVoiceAnalysisItem | null = null;
  @Input() speakerStyleFn!: (name: string | null | undefined) => Record<string, string>;
  @Output() startEdit = new EventEmitter<void>();
  @Output() saveEdit = new EventEmitter<void>();
  @Output() cancelEdit = new EventEmitter<void>();
  @Output() changeVoice = new EventEmitter<void>();

  avatarError = false;
  isPlaying = false;

  constructor(
    private waveSurferService: WaveSurferService,
    private cdr: ChangeDetectorRef,
  ) {}

  get voiceName(): string {
    return normalizeVoiceAssetName(this.speaker.voiceSuggestion);
  }

  get voiceAvatarPath(): string {
    return this.voiceName ? `/assets/voices/${this.voiceName}/avatar.png` : '';
  }

  get voiceDemoPath(): string {
    return this.voiceName ? `/assets/voices/${this.voiceName}/demo.mp3` : '';
  }

  get voiceDemoKey(): string {
    return `voice-demo:${this.voiceName}`;
  }

  get accentColor(): string {
    return ACCENT_COLORS[this.accentClass] ?? '#f0ad5d';
  }

  get roleBadgeLabel(): string {
    return this.speaker.speakerName.toLowerCase().includes('narrator') ? 'Narrator' : 'Dialogue speaker';
  }

  get hasVoice(): boolean {
    return !!this.voiceName;
  }

  displayName(name: string): string {
    return formatSpeakerDisplayName(name);
  }

  initials(name: string): string {
    return speakerInitials(name);
  }

  onAvatarError(): void {
    this.avatarError = true;
    this.cdr.detectChanges();
  }

  onPlayingChange(playing: boolean): void {
    this.isPlaying = playing;
  }

  togglePreview(): void {
    void this.waveSurferService.get(this.voiceDemoKey)?.playPause();
  }
}
