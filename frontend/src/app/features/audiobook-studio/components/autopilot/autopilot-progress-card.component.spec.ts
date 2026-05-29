import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AutopilotProgressCardComponent } from './autopilot-progress-card.component';
import { AutopilotStep } from '../../services/autopilot.service';

function steps(overrides: Partial<Record<string, AutopilotStep['status']>> = {}): AutopilotStep[] {
  const base: Array<[string, string]> = [
    ['analyze-story', 'Analyze story'],
    ['detect-cast', 'Detect cast'],
    ['assign-voices', 'Assign voices'],
    ['generate-preview', 'Generate audio preview'],
  ];
  return base.map(([id, label]) => ({
    id: id as AutopilotStep['id'],
    label,
    description: `${label} desc`,
    status: overrides[id] ?? 'pending'
  }));
}

describe('AutopilotProgressCardComponent', () => {
  let fixture: ComponentFixture<AutopilotProgressCardComponent>;
  let component: AutopilotProgressCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AutopilotProgressCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AutopilotProgressCardComponent);
    component = fixture.componentInstance;
  });

  function byTestId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  }

  it('renders a row per step with status data attributes', () => {
    component.steps = steps({ 'analyze-story': 'completed', 'detect-cast': 'running' });
    fixture.detectChanges();

    expect(byTestId('autopilot-step-analyze-story')!.getAttribute('data-status')).toBe('completed');
    expect(byTestId('autopilot-step-detect-cast')!.getAttribute('data-status')).toBe('running');
    expect(byTestId('autopilot-step-assign-voices')!.getAttribute('data-status')).toBe('pending');
  });

  it('shows the error block and fallback button on a failed step', () => {
    component.steps = steps();
    component.steps[2] = { ...component.steps[2], status: 'failed', errorMessage: 'Voice assignment failed.' };
    fixture.detectChanges();

    const errorBlock = byTestId('autopilot-error');
    expect(errorBlock).not.toBeNull();
    expect(errorBlock!.textContent).toContain('Voice assignment failed.');
    expect(byTestId('open-guided-workflow-button')).not.toBeNull();
  });

  it('emits openGuided and retry from the fallback buttons', () => {
    component.steps = steps();
    component.steps[0] = { ...component.steps[0], status: 'failed', errorMessage: 'Boom.' };
    fixture.detectChanges();

    const openGuided = jasmine.createSpy('openGuided');
    const retry = jasmine.createSpy('retry');
    component.openGuided.subscribe(openGuided);
    component.retry.subscribe(retry);

    byTestId('open-guided-workflow-button')!.click();
    byTestId('autopilot-retry-button')!.click();

    expect(openGuided).toHaveBeenCalled();
    expect(retry).toHaveBeenCalled();
  });

  it('hides the error block when no step failed', () => {
    component.steps = steps({ 'analyze-story': 'completed' });
    fixture.detectChanges();

    expect(byTestId('autopilot-error')).toBeNull();
  });

  it('shows the preview progress text only on the running generate-preview step', () => {
    component.steps = steps({ 'generate-preview': 'running' });
    component.previewProgress = 'Generating part 4 of 8 — 3 of 8 parts ready, 5 still open';
    fixture.detectChanges();

    const progress = byTestId('autopilot-step-progress-generate-preview');
    expect(progress).not.toBeNull();
    expect(progress!.textContent).toContain('Generating part 4 of 8');
    expect(progress!.textContent).toContain('5 still open');
  });

  it('hides the preview progress text while generate-preview is still pending', () => {
    component.steps = steps();
    component.previewProgress = '3 of 8 parts ready';
    fixture.detectChanges();

    expect(byTestId('autopilot-step-progress-generate-preview')).toBeNull();
  });
});
