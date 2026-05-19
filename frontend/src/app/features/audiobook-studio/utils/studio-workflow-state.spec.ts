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
  audioGenerated: false,
  audioAssetsCount: 0
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
      audioGenerated: true,
      audioAssetsCount: 2
    });
    expect(status.story).toBe('completed');
    expect(status.cast).toBe('completed');
    expect(status.script).toBe('completed');
    expect(status.performance).toBe('completed');
    expect(status.audio).toBe('completed');
  });

  it('keeps the performance step completed on reload when only persisted audio assets exist', () => {
    // No regenerated plan, no merged preview URL — but rendered parts were
    // persisted and restored from the snapshot.
    const status = statusByKey({
      storyText: 'Mara: We go now.',
      castCount: 2,
      castReviewed: true,
      scriptTurnCount: 3,
      scriptApproved: true,
      annotatedTurnCount: 3,
      performanceNotesStale: false,
      performanceReady: true,
      audioProductionPlanReady: false,
      audioGenerated: false,
      audioAssetsCount: 3
    });
    expect(status.performance).toBe('completed');
    // Without a merged preview the audio step is still the active one.
    expect(status.audio).toBe('current');
  });

  it('reopens the performance step when notes are stale even if audio assets exist', () => {
    const status = statusByKey({
      ...base,
      storyText: 'Mara: We go now.',
      castCount: 2,
      castReviewed: true,
      scriptTurnCount: 3,
      scriptApproved: true,
      annotatedTurnCount: 3,
      performanceNotesStale: true,
      audioAssetsCount: 3
    });
    expect(status.performance).toBe('warning');
  });
});
