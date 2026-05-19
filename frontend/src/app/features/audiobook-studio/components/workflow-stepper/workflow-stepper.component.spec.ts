import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkflowStep } from '../../models/audiobook-studio.types';
import { WorkflowStepperComponent } from './workflow-stepper.component';

function buildSteps(): WorkflowStep[] {
  return [
    { key: 'story', label: 'Story', sectionId: 'story-section', status: 'completed', statusLabel: 'Story added' },
    { key: 'cast', label: 'Cast', sectionId: 'cast-section', status: 'completed', statusLabel: 'Cast approved' },
    { key: 'script', label: 'Script', sectionId: 'script-section', status: 'warning', statusLabel: 'Script needs review' },
    { key: 'performance', label: 'Performance', sectionId: 'performance-section', status: 'locked', statusLabel: 'Locked' },
    { key: 'audio', label: 'Audio', sectionId: 'audio-section', status: 'locked', statusLabel: 'Locked' }
  ];
}

describe('WorkflowStepperComponent', () => {
  let fixture: ComponentFixture<WorkflowStepperComponent>;
  let component: WorkflowStepperComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowStepperComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowStepperComponent);
    component = fixture.componentInstance;
    component.steps = buildSteps();
    component.isMobile = false;
    fixture.detectChanges();
  });

  function stepButtons(): HTMLButtonElement[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid="workflow-stepper-step"]')
    ) as HTMLButtonElement[];
  }

  it('renders all 5 workflow steps inside the single stepper', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper"]')).not.toBeNull();
    expect(stepButtons().length).toBe(5);
  });

  it('does not render the old journey-grid or workflow-progress markup', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="journey-grid"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="journey-card"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-progress"]')).toBeNull();
  });

  it('renders completed, current and upcoming visual states correctly', () => {
    const buttons = stepButtons();
    expect(buttons[0].getAttribute('data-state')).toBe('completed');
    expect(buttons[1].getAttribute('data-state')).toBe('completed');
    // 'warning' status renders as the visually emphasised current step
    expect(buttons[2].getAttribute('data-state')).toBe('current');
    expect(buttons[2].getAttribute('aria-current')).toBe('step');
    expect(buttons[3].getAttribute('data-state')).toBe('upcoming');
    expect(buttons[4].getAttribute('data-state')).toBe('upcoming');
  });

  it('shows a check icon for completed steps and a number for others', () => {
    const buttons = stepButtons();
    expect(buttons[0].querySelector('svg')).not.toBeNull();
    expect(buttons[2].textContent).toContain('3');
  });

  it('opens a desktop popover when a step is clicked and closes on second click', () => {
    const buttons = stepButtons();
    buttons[2].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).not.toBeNull();

    buttons[2].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
  });

  it('does not open the popover on hover (click only on desktop)', () => {
    const buttons = stepButtons();
    buttons[3].dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
  });

  it('keeps the popover open so the Jump action stays clickable', () => {
    const buttons = stepButtons();
    buttons[2].click();
    fixture.detectChanges();
    const popover = fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]');
    expect(popover).not.toBeNull();
    // Moving the pointer toward the popover/jump button must not dismiss it
    buttons[2].dispatchEvent(new MouseEvent('mouseleave'));
    popover.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).not.toBeNull();
  });

  it('right-aligns the last step popover so it is not clipped', () => {
    stepButtons()[4].click();
    fixture.detectChanges();
    const popover = fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]') as HTMLElement;
    expect(popover.classList).toContain('right-0');
    expect(popover.classList).not.toContain('left-0');
  });

  it('emits scrollTo with the section id when Jump to step is used', () => {
    const emitted: string[] = [];
    component.scrollTo.subscribe((id) => emitted.push(id));

    stepButtons()[2].click();
    fixture.detectChanges();
    const jump = fixture.nativeElement.querySelector(
      '[data-testid="workflow-stepper-jump"]'
    ) as HTMLButtonElement;
    jump.click();
    fixture.detectChanges();

    expect(emitted).toEqual(['script-section']);
    // Detail closes after jumping
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
  });

  it('toggles detail with the keyboard and closes with Escape', () => {
    const button = stepButtons()[2];
    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).not.toBeNull();

    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
  });

  it('renders a mobile bottom-sheet instead of a popover on mobile widths', () => {
    component.isMobile = true;
    fixture.detectChanges();

    stepButtons()[2].click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
    const sheet = fixture.nativeElement.querySelector('[data-testid="workflow-stepper-sheet"]');
    expect(sheet).not.toBeNull();
    expect(sheet.textContent).toContain('Review script');

    const close = fixture.nativeElement.querySelector(
      '[data-testid="workflow-stepper-sheet-close"]'
    ) as HTMLButtonElement;
    close.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-sheet"]')).toBeNull();
  });
});
