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
    component.tier = 'wide';
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

  it('does not pin any step at a fixed width (no shrink-0)', () => {
    for (const li of Array.from(
      fixture.nativeElement.querySelectorAll('ol > li')
    ) as HTMLElement[]) {
      expect(li.className).not.toContain('shrink-0');
    }
  });

  it('shows a check icon for completed steps and a number for others', () => {
    const buttons = stepButtons();
    expect(buttons[0].querySelector('svg')).not.toBeNull();
    expect(buttons[2].textContent).toContain('3');
  });

  it('shows the current step title in the compact label for the compact tier', () => {
    const label = fixture.nativeElement.querySelector('.workflow-stepper-compact-label') as HTMLElement;
    expect(label).not.toBeNull();
    expect(label.textContent?.trim()).toBe('Review script');
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

  it('opens the popover on hover (wide tier)', () => {
    const buttons = stepButtons();
    buttons[3].dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    const popover = fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]');
    expect(popover).not.toBeNull();
    expect(popover.textContent).toContain('Performance');
  });

  it('keeps the popover open while moving the pointer from step into the popover', () => {
    jasmine.clock().install();
    try {
      const buttons = stepButtons();
      buttons[2].dispatchEvent(new MouseEvent('mouseenter'));
      fixture.detectChanges();
      const popover = fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]');
      expect(popover).not.toBeNull();

      buttons[2].dispatchEvent(new MouseEvent('mouseleave'));
      popover.dispatchEvent(new MouseEvent('mouseenter'));
      jasmine.clock().tick(400);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).not.toBeNull();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('closes the popover shortly after the pointer leaves without entering it', () => {
    jasmine.clock().install();
    try {
      const buttons = stepButtons();
      buttons[2].dispatchEvent(new MouseEvent('mouseenter'));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).not.toBeNull();

      buttons[2].dispatchEvent(new MouseEvent('mouseleave'));
      jasmine.clock().tick(400);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
    } finally {
      jasmine.clock().uninstall();
    }
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

  it('renders a bottom-sheet instead of a popover in the compact tier', () => {
    component.tier = 'compact';
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

  it('does not open a popover on hover in the compact tier', () => {
    component.tier = 'compact';
    fixture.detectChanges();
    stepButtons()[3].dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-popover"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="workflow-stepper-sheet"]')).toBeNull();
  });
});
