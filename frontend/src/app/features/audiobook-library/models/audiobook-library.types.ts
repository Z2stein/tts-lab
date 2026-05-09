export type AudiobookProjectStatus = 'DRAFT' | 'GENERATING' | 'NEEDS_REVIEW' | 'APPROVED' | 'EXPORTED' | 'FAILED';
export type AudiobookSpeechSegmentReviewStatus = 'PENDING' | 'NEEDS_CHANGES' | 'APPROVED';
export type AudioAssetType = 'SCENE_MP3' | 'CHAPTER_MP3' | 'FULL_AUDIOBOOK' | 'PREVIEW_MP3' | 'EXPORT_ZIP';
export type AudioAssetStatus = 'GENERATING' | 'READY' | 'FAILED';

export interface AudioAsset {
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

export interface AudiobookDetail extends AudiobookSummary {
  createdAt: string;
  scenes: AudiobookSpeechSegment[];
}

// Backward compatibility alias (deprecated - use AudiobookSpeechSegment instead)
export type AudiobookScene = AudiobookSpeechSegment;
export type AudiobookSceneReviewStatus = AudiobookSpeechSegmentReviewStatus;

export interface AudiobookSummaryResponse {
  items: AudiobookSummary[];
}
