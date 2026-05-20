import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AutopilotModeToggleComponent } from './autopilot-mode-toggle.component';

describe('AutopilotModeToggleComponent', () => {
  let fixture: ComponentFixture<AutopilotModeToggleComponent>;
  let component: AutopilotModeToggleComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AutopilotModeToggleComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AutopilotModeToggleComponent);
    component = fixture.componentInstance;
    component.mode = 'guided';
    fixture.detectChanges();
  });

  function byTestId(id: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement;
  }

  it('emits modeChange when the inactive option is clicked', () => {
    const emitted: string[] = [];
    component.modeChange.subscribe((m) => emitted.push(m));

    byTestId('studio-mode-autopilot').click();

    expect(emitted).toEqual(['autopilot']);
  });

  it('does not emit when the already-active option is clicked', () => {
    const emitted: string[] = [];
    component.modeChange.subscribe((m) => emitted.push(m));

    byTestId('studio-mode-guided').click();

    expect(emitted).toEqual([]);
  });

  it('marks the active option with aria-selected', () => {
    component.mode = 'autopilot';
    fixture.detectChanges();

    expect(byTestId('studio-mode-autopilot').getAttribute('aria-selected')).toBe('true');
    expect(byTestId('studio-mode-guided').getAttribute('aria-selected')).toBe('false');
  });
});
