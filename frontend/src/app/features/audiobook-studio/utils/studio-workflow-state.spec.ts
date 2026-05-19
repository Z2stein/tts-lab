import { buildWorkflowSteps, StudioWorkflowState } from './studio-workflow-state';
import { WorkflowStepKey, WorkflowStepStatus } from '../models/audiobook-studio.types';

function statusByKey(state: StudioWorkflowState): Record<WorkflowStepKey, WorkflowStepStatus> {
  const result = {} as Record<WorkflowStepKey, WorkflowStepStatus>;
  for (const step of buildWorkflowSteps(state)) {
    result[step.key] = step.status;
  }
  return result;
}

const base: StudioWorkflowState = {
  storyText: '',
  castCount: 0,
  castReviewed: false,
  scriptTurnCount: 0,
  scriptApproved: false,
  annotatedTurnCount: 0,
  performanceNotesStale: false,
  performanceReady: false,
  audioProductionPlanReady: false,
  audioGenerated: false
};

describe('buildWorkflowSteps', () => {
  it('keeps only the story step active before characters are detected', () => {
    const status = statusByKey({ ...base, storyText: 'Mara: Hello.' });
    expect(status.story).toBe('current');
    expect(status.cast).toBe('locked');
    expect(status.script).toBe('locked');
  });

  it('completes the story step and activates cast once characters are detected', () => {
    const status = statusByKey({ ...base, storyText: 'Mara: Hello.', castCount: 2 });
    expect(status.story).toBe('completed');
    expect(status.cast).toBe('warning');
  });

  it('does not complete the performance step from emotion notes alone', () => {
    const status = statusByKey({
      ...base,
      storyText: 'Mara: Hello.',
      castCount: 2,
      castReviewed: true,
      scriptTurnCount: 3,
      scriptApproved: true,
      annotatedTurnCount: 3,
      performanceReady: true
    });
    expect(status.performance).toBe('current');
  });

  it('marks every step green when a fully processed project is reloaded (audio generated)', () => {
    // Mirrors the AUDIO_GENERATED snapshot reload: audio production plan is not
    // regenerated (null) but the merged audio URL makes audioGenerated true.
    const status = statusByKey({
      storyText: 'Mara: We go now.\nJonas: Together.',
      castCount: 2,
      castReviewed: true,
      scriptTurnCount: 3,
      scriptApproved: true,
      annotatedTurnCount: 3,
      performanceNotesStale: false,
      performanceReady: true,
      audioProductionPlanReady: false,
      audioGenerated: true
    });
    expect(status.story).toBe('completed');
    expect(status.cast).toBe('completed');
    expect(status.script).toBe('completed');
    expect(status.performance).toBe('completed');
    expect(status.audio).toBe('completed');
  });
});
