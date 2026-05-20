import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { WaveformPlayerComponent } from '../waveform-player/waveform-player.component';
import { AutopilotStep, AutopilotStepId } from '../../services/autopilot.service';
import { AutopilotStepStatusComponent } from './autopilot-step-status.component';

const BUSY_MESSAGE_INTERVAL_MS = 7000;

@Component({
  selector: 'app-autopilot-progress-card',
  standalone: true,
  imports: [CommonModule, AutopilotStepStatusComponent, WaveformPlayerComponent],
  templateUrl: './autopilot-progress-card.component.html',
  host: { class: 'block' }
})
export class AutopilotProgressCardComponent implements OnDestroy {
  @Input() steps: AutopilotStep[] = [];
  @Input() running = false;
  @Input() finished = false;
  @Input() audioUrl: string | null = null;
  @Input() audioFilename: string | null = null;

  @Output() openGuided = new EventEmitter<void>();
  @Output() openStep = new EventEmitter<AutopilotStepId>();
  @Output() retry = new EventEmitter<void>();
  @Output() download = new EventEmitter<void>();

  audioPlaying = false;

  // Rotating reassurance messages while Autopilot works (one every 7s).
  readonly busyMessages: readonly string[] = [
    'Preparing your audiobook preview…',
    'Reading through your story…',
    'Discovering the cast…',
    'Finding the right voice for each character…',
    'Splitting the story into performable scenes…',
    'Shaping the dialogue flow…',
    'Adding emotion and pacing…',
    'Preparing the narrator…',
    'Bringing your characters to life…',
    'Directing the performance…',
    'Rendering your audio preview…',
    'Adding the finishing touches…',
  ];
  busyIndex = 0;

  private _busy = false;
  private busyTimer: ReturnType<typeof setInterval> | null = null;

  @Input() set busy(value: boolean) {
    if (value === this._busy) return;
    this._busy = value;
    if (value) {
      this.startBusyRotation();
    } else {
      this.stopBusyRotation();
    }
  }
  get busy(): boolean {
    return this._busy;
  }

  get busyMessage(): string {
    return this.busyMessages[this.busyIndex];
  }

  get failedStep(): AutopilotStep | null {
    return this.steps.find((step) => step.status === 'failed') ?? null;
  }

  trackStep(_: number, step: AutopilotStep): string {
    return step.id;
  }

  ngOnDestroy(): void {
    this.stopBusyRotation();
  }

  private startBusyRotation(): void {
    this.stopBusyRotation();
    this.busyIndex = 0;
    this.busyTimer = setInterval(() => {
      this.busyIndex = (this.busyIndex + 1) % this.busyMessages.length;
    }, BUSY_MESSAGE_INTERVAL_MS);
  }

  private stopBusyRotation(): void {
    if (this.busyTimer !== null) {
      clearInterval(this.busyTimer);
      this.busyTimer = null;
    }
  }
}
