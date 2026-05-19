import { AnnotatedSpeakerTurn, SpeakerSplitTurn } from '../../audiobook-shared/service/audiobook-workflow.service';

export interface ScriptGroup {
  speaker: string;
  turns: IndexedSpeakerSplitTurn[];
}

export interface IndexedSpeakerSplitTurn {
  index: number;
  turn: SpeakerSplitTurn;
}

export interface AnnotatedMarkup {
  tags: string[];
  text: string;
}

export interface HeroCastMember {
  name: string;
  tone: string;
  initials: string;
  imageUrl?: string;
}

export type WorkflowStepKey = 'story' | 'cast' | 'script' | 'performance' | 'audio';
export type WorkflowStepStatus = 'completed' | 'current' | 'warning' | 'locked' | 'upcoming';
export type RenderRequestStatus = 'not-generated' | 'generating' | 'generated' | 'failed' | 'canceled' | 'timed-out';
export type RequestCancelReason = 'cancel' | 'timeout' | null;

export interface WorkflowStep {
  key: WorkflowStepKey;
  label: string;
  sectionId: string;
  status: WorkflowStepStatus;
  statusLabel: string;
}

export interface WorkflowStepperContent {
  number: number;
  title: string;
  description: string;
}

export interface CurrentTask {
  title: string;
  body: string;
  nextAction: string;
  sectionId: string;
}

export interface RenderRequestAudioState {
  status: RenderRequestStatus;
  partNumber: number;
  speaker: string | null;
  voice: string | null;
  blob: Blob | null;
  error: string | null;
  audioUrl: string | null;
  filename: string | null;
  generatedAt: Date | null;
  startedAt: number | null;
  requestId: number;
  timeoutHandle: number | null;
  controller: AbortController | null;
  cancelReason: RequestCancelReason;
  inFlightPromise: Promise<void> | null;
}

export interface SpeakerAccent {
  color: string;
  shadow: string;
}

// Re-export service types used in shared interfaces so consumers only need one import
export type { AnnotatedSpeakerTurn, SpeakerSplitTurn };
