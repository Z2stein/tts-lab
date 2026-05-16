import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FocusMonitor } from '@angular/cdk/a11y';
import { HeroCastMember } from '../../models/audiobook-studio.types';
import { VoiceSampleService } from '../../services/voice-sample.service';
import { WaveSurferService } from '../../services/wave-surfer.service';

@Component({
  selector: 'app-studio-hero',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './studio-hero.component.html'
})
export class StudioHeroComponent implements AfterViewInit, OnDestroy {
  @Input() heroCast: readonly HeroCastMember[] = [];
  @Input() activeSampleKey: string | null = null;
  @Input() speakerStyleFn: (name: string | null | undefined) => Record<string, string> = () => ({});

  @Output() focusStoryInput = new EventEmitter<Event | undefined>();
  @Output() playVoiceSample = new EventEmitter<{ name: string; event?: Event }>();

  @ViewChild('ctaBtn') private ctaBtnRef!: ElementRef<HTMLButtonElement>;
  @ViewChild('demoWaveform') private demoWaveformRef!: ElementRef<HTMLElement>;

  demoPlaying = false;

  constructor(
    private readonly waveSurferService: WaveSurferService,
    private readonly voiceSampleService: VoiceSampleService,
    private readonly focusMonitor: FocusMonitor,
  ) {}

  ngAfterViewInit(): void {
    this.focusMonitor.monitor(this.ctaBtnRef, true);
    this.initDemoWaveform();
  }

  ngOnDestroy(): void {
    this.focusMonitor.stopMonitoring(this.ctaBtnRef);
    this.waveSurferService.destroy('demo');
  }

  playDemo(event?: Event): void {
    event?.preventDefault();
    const demoWs = this.waveSurferService.get('demo');
    if (demoWs) {
      void demoWs.playPause();
    }
  }

  isVoiceSamplePlaying(name: string): boolean {
    return this.activeSampleKey === `voice:${name}`;
  }

  onPlayVoiceSample(name: string, event?: Event): void {
    this.playVoiceSample.emit({ name, event });
  }

  private initDemoWaveform(): void {
    if (!this.demoWaveformRef || this.waveSurferService.get('demo')) return;
    const ws = this.waveSurferService.create(
      'demo',
      this.demoWaveformRef.nativeElement,
      '/assets/audio/voice-samples/full-text-preview.mp3',
    );
    this.waveSurferService.bind(
      ws,
      () => {
        this.voiceSampleService.pause();
        this.waveSurferService.pauseAll(ws);
      },
      (playing) => { this.demoPlaying = playing; },
    );
  }
}
