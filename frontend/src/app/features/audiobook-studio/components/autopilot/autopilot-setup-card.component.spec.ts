import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { AutopilotSetupCardComponent } from './autopilot-setup-card.component';

describe('AutopilotSetupCardComponent', () => {
  let fixture: ComponentFixture<AutopilotSetupCardComponent>;
  let component: AutopilotSetupCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AutopilotSetupCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AutopilotSetupCardComponent);
    component = fixture.componentInstance;
    component.storyControl = new FormControl('Two words', { nonNullable: true });
    fixture.detectChanges();
  });

  it('shows the same word and character counters as the guided story input', () => {
    const text = fixture.nativeElement.textContent as string;

    expect(component.wordCount).toBe(2);
    expect(component.characterCount).toBe(9);
    expect(text).toContain('2 words');
    expect(text).toContain('9 characters');
  });

  it('exposes the setup card as the hero CTA scroll target', () => {
    const setup = fixture.nativeElement.querySelector('[data-testid="autopilot-setup"]') as HTMLElement | null;

    expect(setup?.id).toBe('autopilot-setup');
  });
});
