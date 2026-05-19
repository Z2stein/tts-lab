import { CurrentTask, WorkflowStep } from '../models/audiobook-studio.types';

export interface StudioWorkflowState {
  storyText: string;
  castCount: number;
  castReviewed: boolean;
  scriptTurnCount: number;
  scriptApproved: boolean;
  annotatedTurnCount: number;
  performanceNotesStale: boolean;
  performanceReady: boolean;
  audioProductionPlanReady: boolean;
  audioGenerated: boolean;
  audioAssetsCount: number;
}

export function buildWorkflowSteps(state: StudioWorkflowState): WorkflowStep[] {
  const castDetected = state.castCount > 0;
  const scriptReady = state.scriptTurnCount > 0;
  const performanceReady = state.performanceReady && !state.performanceNotesStale;
  const audioReady = state.audioGenerated;
  // Performance is "done" once the user advanced past it: an in-session
  // production plan, a (restored) merged preview, or persisted rendered parts.
  // The persisted signals (audioGenerated via mergedAudioUrl, audioAssetsCount)
  // survive a reload, so the step does not regress to gold after reopening.
  const performanceDone =
    state.audioProductionPlanReady || audioReady || state.audioAssetsCount > 0;

  return [
    {
      key: 'story',
      label: 'Story',
      sectionId: 'story-section',
      status: castDetected ? 'completed' : 'current',
      statusLabel: castDetected ? 'Story added' : 'Add story'
    },
    {
      key: 'cast',
      label: 'Cast',
      sectionId: 'cast-section',
      // Stays muted until characters are detected, so only the story step is
      // active while the user is still adding/generating the story.
      status: state.castReviewed ? 'completed' : castDetected ? 'warning' : 'locked',
      statusLabel: state.castReviewed ? 'Cast approved' : castDetected ? 'Cast needs review' : 'Find characters'
    },
    {
      key: 'script',
      label: 'Script',
      sectionId: 'script-section',
      status: !state.castReviewed && !scriptReady ? 'locked' : state.scriptApproved ? 'completed' : scriptReady ? 'warning' : 'current',
      statusLabel: !state.castReviewed && !scriptReady ? 'Locked' : state.scriptApproved ? 'Script approved' : scriptReady ? 'Script needs review' : 'Review script'
    },
    {
      key: 'performance',
      label: 'Performance',
      sectionId: 'performance-section',
      status: !state.scriptApproved ? 'locked' : state.performanceNotesStale ? 'warning' : performanceDone ? 'completed' : 'current',
      statusLabel: !state.scriptApproved ? 'Locked' : state.performanceNotesStale ? 'Notes stale' : performanceDone ? 'Performance ready' : 'Add emotion'
    },
    {
      key: 'audio',
      label: 'Audio',
      sectionId: 'audio-section',
      status: !performanceReady ? 'locked' : audioReady ? 'completed' : 'current',
      statusLabel: !performanceReady ? 'Locked' : audioReady ? 'Preview ready' : state.audioProductionPlanReady ? 'Generate preview' : 'Prepare audio'
    }
  ];
}

export function buildCurrentTask(state: StudioWorkflowState): CurrentTask {
  const storyAdded = state.storyText.trim().length > 0;

  if (!storyAdded) {
    return {
      title: 'Current task: Start with your story',
      body: 'Paste your text or use the sample story. TTS Lab will find the narrator, characters, and a project title for you.',
      nextAction: 'Paste text or use the sample story, then click Find narrator & characters.',
      sectionId: 'story-section'
    };
  }

  if (state.castCount === 0) {
    return {
      title: 'Current task: Find narrator & characters',
      body: 'TTS Lab will detect who is speaking in your story and suggest matching voices.',
      nextAction: 'Click Find narrator & characters to get started.',
      sectionId: 'story-section'
    };
  }

  if (!state.castReviewed) {
    return {
      title: 'Current task: Choose your voices',
      body: 'We found the speakers in your story. Preview each voice or change it to something that fits better.',
      nextAction: 'Approve the voices or make changes, then continue to review the script.',
      sectionId: 'cast-section'
    };
  }

  if (state.scriptTurnCount === 0) {
    return {
      title: 'Current task: Creating the script',
      body: 'TTS Lab is preparing your story, splitting it into speaker turns so every line can be performed by the right voice.',
      nextAction: 'Click Next: Review script when ready.',
      sectionId: 'cast-section'
    };
  }

  if (!state.scriptApproved) {
    return {
      title: 'Current task: Check the script',
      body: 'Each line has been assigned to a speaker. Verify this looks correct.',
      nextAction: 'Approve the script or go back to adjust voices.',
      sectionId: 'script-section'
    };
  }

  if (!state.performanceReady || state.performanceNotesStale) {
    return {
      title: 'Current task: Add emotion & pacing',
      body: 'Fine-tune how each line should sound. Add notes like calm, urgent, or whispered to guide the voice generation.',
      nextAction: state.performanceNotesStale ? 'Update emotion & pacing, then continue.' : 'Add emotion notes or skip if you\'re ready to generate.',
      sectionId: 'performance-section'
    };
  }

  if (!state.audioProductionPlanReady) {
    return {
      title: 'Current task: Preparing audiobook',
      body: 'TTS Lab is converting your script and performance notes into an audiobook, ready to preview.',
      nextAction: 'Click Next: Prepare audiobook when ready.',
      sectionId: 'performance-section'
    };
  }

  return {
    title: 'Current task: Generate your audiobook preview',
    body: 'Your script, voices, and performance notes are ready. Generate a preview and listen before downloading.',
    nextAction: 'Generate a preview to hear how it sounds. You can always edit and regenerate.',
    sectionId: 'audio-section'
  };
}
