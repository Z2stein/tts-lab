import { Injectable, signal } from '@angular/core';

export type AutopilotStepStatus = 'pending' | 'running' | 'completed' | 'failed';

export type AutopilotStepId =
  | 'analyze-story'
  | 'detect-cast'
  | 'assign-voices'
  | 'split-script'
  | 'add-emotion'
  | 'generate-preview';

export interface AutopilotStep {
  id: AutopilotStepId;
  label: string;
  description: string;
  status: AutopilotStepStatus;
  icon?: string;
  errorMessage?: string;
}

const STEP_DEFINITIONS: ReadonlyArray<Pick<AutopilotStep, 'id' | 'label' | 'description' | 'icon'>> = [
  { id: 'analyze-story', label: 'Analyze story', description: 'Understanding themes, tone, and structure.', icon: '📖' },
  { id: 'detect-cast', label: 'Detect cast', description: 'Finding characters and narrators.', icon: '👥' },
  { id: 'assign-voices', label: 'Assign voices', description: 'Matching voices to characters.', icon: '🎙️' },
  { id: 'split-script', label: 'Split script', description: 'Breaking the script into speakable segments.', icon: '✂️' },
  { id: 'add-emotion', label: 'Add emotion & pacing', description: 'Applying emotion, pacing, and performance style.', icon: '✨' },
  { id: 'generate-preview', label: 'Generate audio preview', description: 'Rendering a full audiobook preview.', icon: '🎵' },
];

// Which Guided-workflow section a failed step maps to, so the fallback lands
// the user where they can inspect and fix the failure manually.
const GUIDED_SECTION_FOR_STEP: Record<AutopilotStepId, string> = {
  'analyze-story': 'story-section',
  'detect-cast': 'cast-section',
  'assign-voices': 'cast-section',
  'split-script': 'script-section',
  'add-emotion': 'performance-section',
  'generate-preview': 'audio-section',
};

@Injectable()
export class AutopilotService {
  private readonly _steps = signal<AutopilotStep[]>(this.freshSteps());
  private readonly _running = signal(false);
  private readonly _finished = signal(false);

  readonly steps = this._steps.asReadonly();
  readonly running = this._running.asReadonly();
  readonly finished = this._finished.asReadonly();

  readonly stepOrder: ReadonlyArray<AutopilotStepId> = STEP_DEFINITIONS.map((s) => s.id);

  reset(): void {
    this._steps.set(this.freshSteps());
    this._running.set(false);
    this._finished.set(false);
  }

  setRunning(running: boolean): void {
    this._running.set(running);
  }

  markFinished(): void {
    this._running.set(false);
    this._finished.set(true);
  }

  markRunning(id: AutopilotStepId): void {
    this.patch(id, { status: 'running', errorMessage: undefined });
  }

  markCompleted(id: AutopilotStepId): void {
    this.patch(id, { status: 'completed', errorMessage: undefined });
  }

  markFailed(id: AutopilotStepId, message: string): void {
    this.patch(id, { status: 'failed', errorMessage: message });
    this._running.set(false);
  }

  firstFailedStepId(): AutopilotStepId | null {
    return this._steps().find((step) => step.status === 'failed')?.id ?? null;
  }

  guidedSectionForFailedStep(): string {
    const failed = this.firstFailedStepId();
    return failed ? GUIDED_SECTION_FOR_STEP[failed] : 'story-section';
  }

  private patch(id: AutopilotStepId, changes: Partial<AutopilotStep>): void {
    this._steps.update((steps) =>
      steps.map((step) => (step.id === id ? { ...step, ...changes } : step))
    );
  }

  private freshSteps(): AutopilotStep[] {
    return STEP_DEFINITIONS.map((step) => ({ ...step, status: 'pending' as AutopilotStepStatus }));
  }
}
