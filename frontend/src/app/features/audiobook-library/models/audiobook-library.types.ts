// Status types
export type AudiobookProjectStatus = 'DRAFT' | 'GENERATING' | 'NEEDS_REVIEW' | 'APPROVED' | 'EXPORTED' | 'FAILED';
export type AudioAssetType = 'VOICE_PREVIEW' | 'SEGMENT_AUDIO' | 'FULL_AUDIOBOOK';
export type RunType = 'CAST_DISCOVERY' | 'SCRIPT_SPLIT' | 'ANNOTATION' | 'RENDER_PLAN' | 'AUDIO_GENERATION';
export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'STALE';

// Legacy aliases for backward compatibility
export type AudiobookSpeechSegmentReviewStatus = 'PENDING' | 'NEEDS_CHANGES' | 'APPROVED';
export type AudioAssetStatus = 'GENERATING' | 'READY' | 'FAILED';

// Core entities
export interface Character {
  id: string;
  projectId: string;
  name: string;
  roleDescription?: string;
  voiceKey: string;
  sortOrder: number;
  approved: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SpeechSegment {
  id: string;
  projectId: string;
  characterId: string;
  characterName?: string; // Enriched with character name
  sequenceNo: number;
  originalText: string;
  annotatedText?: string;
  edited: boolean;
  approved: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AudioAsset {
  id: string;
  projectId: string;
  type: AudioAssetType;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  durationMs?: number | null;
  createdAt: string;
  downloadUrl: string | null;
  streamUrl: string | null;
  // Legacy fields for backward compatibility
  sceneId?: string | null;
  version?: number;
  filename?: string;
  durationSeconds?: number | null;
  status?: AudioAssetStatus;
}

export interface RenderSegment {
  id: string;
  speechSegmentId: string;
  status: RunStatus;
  audioAssetId?: string;
  audioAsset?: AudioAsset;
}

export interface AIGenerationRun {
  id: string;
  projectId: string;
  type: RunType;
  status: RunStatus;
  startedAt?: string;
  completedAt?: string;
  renders: RenderSegment[];
}

export interface AudiobookProject {
  id: string;
  title: string;
  sourceText: string;
  languageCode: string;
  modelName: string;
  audioEncoding: string;
  status: AudiobookProjectStatus;
  revision: number;
  createdAt: string;
  updatedAt: string;
}

// Library display response (NEW - rich detail view)
export interface AudiobookLibraryDisplay {
  project: AudiobookProject;
  characters: Character[];
  segments: SpeechSegment[];
  generationRuns: AIGenerationRun[];
  assets: AudioAsset[];
}

// Legacy types for backward compatibility
export interface AudioAssetLegacy {
  id: string;
  sceneId: string | null;
  type: AudioAssetType;
  version: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  durationSeconds: number | null;
  status: AudioAssetStatus;
  createdAt: string;
  downloadUrl: string;
  streamUrl: string;
}

export interface AudiobookSpeechSegment {
  id: string;
  orderIndex: number;
  title: string;
  reviewStatus: AudiobookSpeechSegmentReviewStatus;
  durationSeconds: number | null;
  speakerName?: string;
  speakerRoleDescription?: string;
  voiceName?: string;
  performanceDirections?: string;
}

export interface AudiobookSummary {
  id: string;
  title: string;
  status: AudiobookProjectStatus;
  sceneCount: number;
  speakerCount: number | null;
  totalDurationSeconds: number | null;
  updatedAt: string;
  audioAssets: AudioAsset[];
}

export interface AudiobookDetail extends AudiobookSummary {
  createdAt: string;
  scenes: AudiobookSpeechSegment[];
}

// Backward compatibility alias
export type AudiobookScene = AudiobookSpeechSegment;
export type AudiobookSceneReviewStatus = AudiobookSpeechSegmentReviewStatus;

export interface AudiobookSummaryResponse {
  items: AudiobookSummary[];
}
