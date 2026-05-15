import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SpeakerVoiceCatalogItem } from '../../../../shared/api-contract.generated';
import { VoicePickerService } from '../../services/voice-picker.service';
import { VoiceOptionCardComponent } from './voice-option-card.component';
import { VoiceSearchInputComponent } from './voice-search-input.component';

@Component({
  selector: 'app-voice-picker-modal',
  standalone: true,
  imports: [CommonModule, VoiceOptionCardComponent, VoiceSearchInputComponent],
  templateUrl: './voice-picker-modal.component.html',
})
export class VoicePickerModalComponent implements OnChanges, OnDestroy {
  @Input() open = false;
  @Input() speakerName = '';
  @Input() currentVoiceId = '';
  @Output() voiceSelected = new EventEmitter<SpeakerVoiceCatalogItem>();
  @Output() closed = new EventEmitter<void>();

  voices: SpeakerVoiceCatalogItem[] = [];
  searchQuery = '';
  loading = false;
  error: string | null = null;
  pendingVoiceId = '';

  private demoAudio: HTMLAudioElement | null = null;
  private playingVoiceId = '';

  constructor(private readonly voicePickerService: VoicePickerService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']?.currentValue === true) {
      this.pendingVoiceId = this.currentVoiceId;
      this.searchQuery = '';
      this.stopDemo();
      if (this.voices.length === 0) {
        void this.loadVoices();
      }
    }
    if (changes['open']?.currentValue === false) {
      this.stopDemo();
    }
  }

  ngOnDestroy(): void {
    this.stopDemo();
  }

  get filteredVoices(): SpeakerVoiceCatalogItem[] {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) return this.voices;
    return this.voices.filter(
      (v) =>
        v.displayName.toLowerCase().includes(q) ||
        v.providerVoiceName.toLowerCase().includes(q) ||
        v.description.toLowerCase().includes(q)
    );
  }

  isSelected(voice: SpeakerVoiceCatalogItem): boolean {
    return voice.id === this.pendingVoiceId;
  }

  isDemoPlaying(voice: SpeakerVoiceCatalogItem): boolean {
    return this.playingVoiceId === voice.id;
  }

  selectVoice(voice: SpeakerVoiceCatalogItem): void {
    this.pendingVoiceId = voice.id;
  }

  toggleDemo(voice: SpeakerVoiceCatalogItem): void {
    if (this.playingVoiceId === voice.id) {
      this.stopDemo();
      return;
    }
    this.stopDemo();
    this.demoAudio = new Audio(voice.demoMp3Url);
    this.playingVoiceId = voice.id;
    this.demoAudio.onended = () => { this.playingVoiceId = ''; };
    this.demoAudio.onerror = () => { this.playingVoiceId = ''; };
    void this.demoAudio.play();
  }

  trackByVoiceId(_: number, voice: SpeakerVoiceCatalogItem): string {
    return voice.id;
  }

  confirm(): void {
    const voice = this.voices.find((v) => v.id === this.pendingVoiceId);
    if (voice) {
      this.voiceSelected.emit(voice);
    }
    this.close();
  }

  close(): void {
    this.stopDemo();
    this.closed.emit();
  }

  private async loadVoices(): Promise<void> {
    this.loading = true;
    this.error = null;
    try {
      this.voices = await this.voicePickerService.getVoiceCatalog();
    } catch {
      this.error = 'Could not load voices. Please try again.';
    } finally {
      this.loading = false;
    }
  }

  private stopDemo(): void {
    if (this.demoAudio) {
      this.demoAudio.pause();
      this.demoAudio.src = '';
      this.demoAudio = null;
    }
    this.playingVoiceId = '';
  }
}
