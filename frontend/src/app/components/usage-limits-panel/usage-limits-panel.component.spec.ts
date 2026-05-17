import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UsageLimitsPanelComponent } from './usage-limits-panel.component';
import { RequestRateLimitSummary } from '../../shared/api-contract.generated';

describe('UsageLimitsPanelComponent', () => {
  let component: UsageLimitsPanelComponent;
  let fixture: ComponentFixture<UsageLimitsPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsageLimitsPanelComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(UsageLimitsPanelComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with expanded state as false', () => {
    expect(component.isExpanded).toBe(false);
  });

  it('should toggle expanded state', () => {
    component.toggleExpanded();
    expect(component.isExpanded).toBe(true);
    component.toggleExpanded();
    expect(component.isExpanded).toBe(false);
  });

  it('should calculate overall usage percent correctly', () => {
    const mockSummary: RequestRateLimitSummary = {
      windowResetAt: new Date(Date.now() + 3600000).toISOString(),
      windowSeconds: 3600,
      limits: [
        { modelType: 'SPEECH_MODEL', used: 300, limit: 600, remaining: 300, unit: 'WORDS' },
        { modelType: 'TEXT_MODEL', used: 264, limit: 600, remaining: 336, unit: 'WORDS' }
      ]
    };

    component.limitSummary = mockSummary;
    expect(component.getOverallUsagePercent()).toBe(50);
  });

  it('should return 0% when no limits', () => {
    component.limitSummary = { windowResetAt: new Date().toISOString(), windowSeconds: 3600, limits: [] };
    expect(component.getOverallUsagePercent()).toBe(0);
  });

  it('should return 0% when limitSummary is null', () => {
    component.limitSummary = null;
    expect(component.getOverallUsagePercent()).toBe(0);
  });

  it('should calculate progress percent correctly', () => {
    const limit = { modelType: 'SPEECH_MODEL' as const, used: 264, limit: 600, remaining: 336, unit: 'WORDS' as const };
    expect(component.getProgressPercent(limit)).toBe(44);
  });

  it('should return correct display names', () => {
    expect(component.getDisplayName('SPEECH_MODEL')).toBe('Speech generation');
    expect(component.getDisplayName('TEXT_MODEL')).toBe('Text analysis');
    expect(component.getDisplayName('UNKNOWN')).toBe('UNKNOWN');
  });

  it('should clean up countdown interval on destroy', () => {
    component.ngOnInit();
    const intervalId = component['countdownInterval'] as number;
    expect(intervalId).not.toBeNull();
    spyOn(window, 'clearInterval');
    component.ngOnDestroy();
    expect(window.clearInterval).toHaveBeenCalledWith(intervalId);
  });

  it('should update countdown with hours and minutes', () => {
    const mockSummary: RequestRateLimitSummary = {
      windowResetAt: new Date(Date.now() + 3600000 * 5 + 30 * 60000).toISOString(), // 5h 30m from now
      windowSeconds: 3600,
      limits: []
    };
    component.limitSummary = mockSummary;
    component['updateCountdown']();
    expect(component.resetCountdown).toContain('Resets in');
    expect(component.resetCountdown).toContain('h');
  });
});
