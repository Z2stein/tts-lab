import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap, shareReplay, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

/**
 * Workflow state machine for audiobook generation.
 * Linear progression: DRAFT -> ... -> AUDIO_GENERATED
 */
export enum WorkflowState {
  DRAFT = 'DRAFT',
  ANALYZING_SPEAKERS = 'ANALYZING_SPEAKERS',
  SPEAKERS_DISCOVERED = 'SPEAKERS_DISCOVERED',
  SPLITTING_DIALOGUE = 'SPLITTING_DIALOGUE',
  DIALOGUE_SPLIT = 'DIALOGUE_SPLIT',
  ANNOTATING_DIALOGUE = 'ANNOTATING_DIALOGUE',
  DIALOGUE_ANNOTATED = 'DIALOGUE_ANNOTATED',
  CONFIGURING_OUTPUT = 'CONFIGURING_OUTPUT',
  CONFIGURED = 'CONFIGURED',
  RENDERING_AUDIO = 'RENDERING_AUDIO',
  AUDIO_GENERATED = 'AUDIO_GENERATED',
  ARCHIVED = 'ARCHIVED'
}

export interface WorkflowSessionResponse {
  id: string;
  projectId: string;
  userId: string;
  state: WorkflowState;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  availableActions: string[];
}

export interface CreateWorkflowRequest {
  storyText: string;
}

export interface SpeakerVoiceAnalysisItem {
  speakerName: string;
  roleDescription: string;
  voiceSuggestion: { getKey: () => string };
}

export interface SplitDialogueRequest {
  speakers: SpeakerVoiceAnalysisItem[];
}

export interface SpeakerSplitTurn {
  speaker: string;
  text: string;
}

export interface AnnotateDialogueRequest {
  turns: SpeakerSplitTurn[];
}

export interface ConfigureOutputRequest {
  languageCode: string;
  modelName: string;
  audioEncoding: string;
  voiceAssignments: Map<string, string>;
}

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private sessionSubject = new BehaviorSubject<WorkflowSessionResponse | null>(null);
  public session$ = this.sessionSubject.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Create a new audiobook generation workflow.
   */
  createWorkflow(storyText: string): Observable<WorkflowSessionResponse> {
    const request: CreateWorkflowRequest = { storyText };
    return this.http.post<WorkflowSessionResponse>(
      '/api/audiobooks/workflows',
      request
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Failed to create workflow', err);
        return of(null as any);
      })
    );
  }

  /**
   * Fetch current workflow state.
   */
  getWorkflow(sessionId: string): Observable<WorkflowSessionResponse> {
    return this.http.get<WorkflowSessionResponse>(
      `/api/audiobooks/workflows/${sessionId}`
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Failed to fetch workflow', err);
        return of(null as any);
      })
    );
  }

  /**
   * Step 1: Discover speakers.
   */
  discoverSpeakers(sessionId: string): Observable<WorkflowSessionResponse> {
    return this.http.post<WorkflowSessionResponse>(
      `/api/audiobooks/workflows/${sessionId}/discover-speakers`,
      {}
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Speaker discovery failed', err);
        return of(null as any);
      })
    );
  }

  /**
   * Step 2: Split dialogue.
   */
  splitDialogue(sessionId: string, speakers: SpeakerVoiceAnalysisItem[]): Observable<WorkflowSessionResponse> {
    const request: SplitDialogueRequest = { speakers };
    return this.http.post<WorkflowSessionResponse>(
      `/api/audiobooks/workflows/${sessionId}/split-dialogue`,
      request
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Dialogue split failed', err);
        return of(null as any);
      })
    );
  }

  /**
   * Step 3: Annotate dialogue.
   */
  annotateDialogue(sessionId: string, turns: SpeakerSplitTurn[]): Observable<WorkflowSessionResponse> {
    const request: AnnotateDialogueRequest = { turns };
    return this.http.post<WorkflowSessionResponse>(
      `/api/audiobooks/workflows/${sessionId}/annotate-dialogue`,
      request
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Dialogue annotation failed', err);
        return of(null as any);
      })
    );
  }

  /**
   * Step 4: Configure output.
   */
  configureOutput(
    sessionId: string,
    languageCode: string,
    modelName: string,
    audioEncoding: string,
    voiceAssignments: Map<string, string>
  ): Observable<WorkflowSessionResponse> {
    const request: ConfigureOutputRequest = {
      languageCode,
      modelName,
      audioEncoding,
      voiceAssignments
    };
    return this.http.post<WorkflowSessionResponse>(
      `/api/audiobooks/workflows/${sessionId}/configure-output`,
      request
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Output configuration failed', err);
        return of(null as any);
      })
    );
  }

  /**
   * Step 5: Generate audio.
   */
  generateAudio(sessionId: string): Observable<WorkflowSessionResponse> {
    return this.http.post<WorkflowSessionResponse>(
      `/api/audiobooks/workflows/${sessionId}/generate-audio`,
      {}
    ).pipe(
      tap(session => this.sessionSubject.next(session)),
      shareReplay(1),
      catchError(err => {
        console.error('Audio generation failed', err);
        return of(null as any);
      })
    );
  }

  /**
   * Get current session from subject.
   */
  getCurrentSession(): WorkflowSessionResponse | null {
    return this.sessionSubject.getValue();
  }

  /**
   * Clear session.
   */
  clearSession(): void {
    this.sessionSubject.next(null);
  }
}
