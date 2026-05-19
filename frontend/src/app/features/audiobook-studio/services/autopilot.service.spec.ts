import { AutopilotService } from './autopilot.service';

describe('AutopilotService', () => {
  let service: AutopilotService;

  beforeEach(() => {
    service = new AutopilotService();
  });

  it('starts with six pending steps and no run flags', () => {
    expect(service.steps().length).toBe(6);
    expect(service.steps().every((s) => s.status === 'pending')).toBeTrue();
    expect(service.running()).toBeFalse();
    expect(service.finished()).toBeFalse();
  });

  it('transitions a step through running then completed', () => {
    service.markRunning('analyze-story');
    expect(service.steps()[0].status).toBe('running');

    service.markCompleted('analyze-story');
    expect(service.steps()[0].status).toBe('completed');
  });

  it('marks a step failed with a message and stops running', () => {
    service.setRunning(true);
    service.markFailed('assign-voices', 'Voice assignment failed.');

    const failed = service.steps().find((s) => s.id === 'assign-voices');
    expect(failed?.status).toBe('failed');
    expect(failed?.errorMessage).toBe('Voice assignment failed.');
    expect(service.running()).toBeFalse();
    expect(service.firstFailedStepId()).toBe('assign-voices');
  });

  it('maps the failed step to the matching guided section', () => {
    service.markFailed('split-script', 'Split failed.');
    expect(service.guidedSectionForFailedStep()).toBe('script-section');
  });

  it('reset returns all steps to pending and clears flags', () => {
    service.markRunning('analyze-story');
    service.markFinished();
    service.reset();

    expect(service.steps().every((s) => s.status === 'pending')).toBeTrue();
    expect(service.running()).toBeFalse();
    expect(service.finished()).toBeFalse();
  });
});
