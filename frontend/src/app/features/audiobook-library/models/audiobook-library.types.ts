export type AudiobookProjectStatus = 'DRAFT' | 'GENERATING' | 'NEEDS_REVIEW' | 'APPROVED' | 'EXPORTED' | 'FAILED';
export type AudiobookSceneReviewStatus = 'PENDING' | 'NEEDS_CHANGES' | 'APPROVED';
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

export interface AudiobookScene {
  id: string;
  orderIndex: number;
  title: string;
  reviewStatus: AudiobookSceneReviewStatus;
  durationSeconds: number | null;
}

export interface AudiobookDetail extends AudiobookSummary {
  createdAt: string;
  scenes: AudiobookScene[];
}

export interface AudiobookSummaryResponse {
  items: AudiobookSummary[];
}
