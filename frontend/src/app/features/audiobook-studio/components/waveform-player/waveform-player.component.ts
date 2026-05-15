import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { NgIf } from '@angular/common';
import { durationLabelFor } from '../../utils/audio-format';
import { VoiceSampleService } from '../../services/voice-sample.service';
import { WaveSurferService } from '../../services/wave-surfer.service';

/**
 * Reusable waveform player. Owns the WaveSurfer lifecycle for the given key.
 *
 * Slot structure (projected via ng-content):
 *   [slot=header] — optional metadata block shown before the play button
 *   (default)     — optional content shown after the wave-time label (e.g. download button)
 *
 * The host element should carry the layout class (e.g. "generated-audio-player framework-player")
 * so the CSS grid targets it directly.
 */
@Component({
  selector: 'app-waveform-player',
  standalone: true,
  imports: [NgIf],
  template: `
    <ng-content select="[slot=header]"></ng-content>
    <button
      *ngIf="showPlayButton"
      type="button"
      class="player-button"
      [class.is-playing]="playing"
      [attr.aria-label]="playing ? 'Pause ' + ariaLabel : 'Play ' + ariaLabel"
      (click)="onPlayPause()"
    >
      <span class="play-icon" aria-hidden="true"></span>
    </button>
    <div #waveformEl class="waveform-canvas generated-waveform" aria-hidden="true"></div>
    <span *ngIf="showTime" class="wave-time">00:00 / {{ durationLabel }}</span>
    <ng-content></ng-content>
  `,
  styleUrl: './../../audiobook-studio-page.component.css',
})
export class WaveformPlayerComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) key!: string;
  @Input() src: string | null = null;
  @Input() playing = false;
  @Input() ariaLabel = 'audio';
  @Input() progressColor: string | null = null;
  @Input() waveHeight: number | null = null;
  @Input() showPlayButton = true;
  @Input() showTime = true;

  @Output() playingChange = new EventEmitter<boolean>();

  @ViewChild('waveformEl') private waveformEl!: ElementRef<HTMLElement>;

  private viewInitialized = false;

  get durationLabel(): string {
    return durationLabelFor(this.waveSurferService.get(this.key));
  }

  constructor(
    private readonly waveSurferService: WaveSurferService,
    private readonly voiceSampleService: VoiceSampleService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    if (this.src) {
      window.setTimeout(() => this.createWaveSurfer());
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['src'] && !changes['src'].firstChange && this.viewInitialized) {
      this.waveSurferService.destroy(this.key);
      if (this.src) {
        window.setTimeout(() => this.createWaveSurfer());
      }
    }
  }

  ngOnDestroy(): void {
    this.waveSurferService.destroy(this.key);
  }

  onPlayPause(): void {
    void this.waveSurferService.get(this.key)?.playPause();
  }

  private createWaveSurfer(): void {
    if (!this.src || !this.waveformEl) return;
    const colorOverrides = this.progressColor
      ? { progressColor: this.progressColor, cursorColor: this.progressColor, height: this.waveHeight ?? undefined }
      : this.waveHeight !== null
        ? { height: this.waveHeight }
        : undefined;
    const ws = this.waveSurferService.create(this.key, this.waveformEl.nativeElement, this.src, colorOverrides);

    // Update duration label when audio is ready
    ws.on('ready', () => {
      this.cdr.markForCheck();
    });

    this.waveSurferService.bind(
      ws,
      () => {
        this.voiceSampleService.pause();
        this.waveSurferService.pauseAll(ws);
      },
      (playing) => this.playingChange.emit(playing),
    );
  }
}
