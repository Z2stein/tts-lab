import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { WaveformPlayerComponent } from '../waveform-player/waveform-player.component';
import { AutopilotStep } from '../../services/autopilot.service';
import { AutopilotStepStatusComponent } from './autopilot-step-status.component';

@Component({
  selector: 'app-autopilot-progress-card',
  standalone: true,
  imports: [CommonModule, AutopilotStepStatusComponent, WaveformPlayerComponent],
  templateUrl: './autopilot-progress-card.component.html',
  host: { class: 'block' }
})
export class AutopilotProgressCardComponent {
  @Input() steps: AutopilotStep[] = [];
  @Input() running = false;
  @Input() finished = false;
  @Input() audioUrl: string | null = null;
  @Input() audioFilename: string | null = null;

  @Output() openGuided = new EventEmitter<void>();
  @Output() retry = new EventEmitter<void>();
  @Output() download = new EventEmitter<void>();

  audioPlaying = false;

  get failedStep(): AutopilotStep | null {
    return this.steps.find((step) => step.status === 'failed') ?? null;
  }

  trackStep(_: number, step: AutopilotStep): string {
    return step.id;
  }
}
