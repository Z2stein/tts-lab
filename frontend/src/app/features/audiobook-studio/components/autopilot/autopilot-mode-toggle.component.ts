import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

export type StudioMode = 'guided' | 'autopilot';

@Component({
  selector: 'app-autopilot-mode-toggle',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './autopilot-mode-toggle.component.html',
  host: { class: 'block' }
})
export class AutopilotModeToggleComponent {
  @Input() mode: StudioMode = 'guided';
  @Output() modeChange = new EventEmitter<StudioMode>();

  select(mode: StudioMode): void {
    if (mode !== this.mode) {
      this.modeChange.emit(mode);
    }
  }
}
