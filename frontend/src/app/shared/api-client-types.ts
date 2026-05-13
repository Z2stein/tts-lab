export interface CreatedAudioDownload {
  blob: Blob;
  filename: string;
  projectId?: string;
}

export interface RequestOptions {
  signal?: AbortSignal;
}
