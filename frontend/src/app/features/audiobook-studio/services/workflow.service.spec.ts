import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { WorkflowService, WorkflowState, WorkflowSessionResponse } from './workflow.service';

describe('WorkflowService', () => {
  let service: WorkflowService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [WorkflowService]
    });

    service = TestBed.inject(WorkflowService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should POST to /api/audiobooks/workflows with storyText and return WorkflowSessionResponse', (done) => {
    const storyText = 'Alice said hello. Bob replied hi.';
    const mockResponse: WorkflowSessionResponse = {
      id: 'session-1',
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.DRAFT,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:00:00Z',
      completedAt: null,
      availableActions: ['discover-speakers']
    };

    service.createWorkflow(storyText).subscribe(result => {
      expect(result).toEqual(mockResponse);
      expect(result.state).toBe(WorkflowState.DRAFT);
      done();
    });

    const req = httpMock.expectOne('/api/audiobooks/workflows');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ storyText });
    req.flush(mockResponse);
  });

  it('should update sessionSubject when createWorkflow succeeds', (done) => {
    const storyText = 'Test story';
    const mockResponse: WorkflowSessionResponse = {
      id: 'session-1',
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.DRAFT,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:00:00Z',
      completedAt: null,
      availableActions: []
    };

    let sessionSubjectValue: WorkflowSessionResponse | null = null;
    service.session$.subscribe(session => {
      sessionSubjectValue = session;
    });

    service.createWorkflow(storyText).subscribe(() => {
      expect(sessionSubjectValue).toEqual(mockResponse);
      done();
    });

    const req = httpMock.expectOne('/api/audiobooks/workflows');
    req.flush(mockResponse);
  });

  it('should GET from /api/audiobooks/workflows/{sessionId}', (done) => {
    const sessionId = 'session-1';
    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.SPEAKERS_DISCOVERED,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:05:00Z',
      completedAt: null,
      availableActions: ['split-dialogue']
    };

    service.getWorkflow(sessionId).subscribe(result => {
      expect(result.state).toBe(WorkflowState.SPEAKERS_DISCOVERED);
      done();
    });

    const req = httpMock.expectOne(`/api/audiobooks/workflows/${sessionId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should POST to /api/audiobooks/workflows/{sessionId}/discover-speakers', (done) => {
    const sessionId = 'session-1';
    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.ANALYZING_SPEAKERS,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:02:00Z',
      completedAt: null,
      availableActions: []
    };

    service.discoverSpeakers(sessionId).subscribe(result => {
      expect(result.state).toBe(WorkflowState.ANALYZING_SPEAKERS);
      done();
    });

    const req = httpMock.expectOne(`/api/audiobooks/workflows/${sessionId}/discover-speakers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(mockResponse);
  });

  it('should POST to /api/audiobooks/workflows/{sessionId}/split-dialogue with speakers array', (done) => {
    const sessionId = 'session-1';
    const speakers = [
      { speakerName: 'Alice', roleDescription: 'Protagonist', voiceSuggestion: { getKey: () => 'voice-1' } },
      { speakerName: 'Bob', roleDescription: 'Friend', voiceSuggestion: { getKey: () => 'voice-2' } }
    ];
    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.DIALOGUE_SPLIT,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:03:00Z',
      completedAt: null,
      availableActions: ['annotate-dialogue']
    };

    service.splitDialogue(sessionId, speakers).subscribe(result => {
      expect(result.state).toBe(WorkflowState.DIALOGUE_SPLIT);
      done();
    });

    const req = httpMock.expectOne(`/api/audiobooks/workflows/${sessionId}/split-dialogue`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ speakers });
    req.flush(mockResponse);
  });

  it('should POST to /api/audiobooks/workflows/{sessionId}/annotate-dialogue with turns array', (done) => {
    const sessionId = 'session-1';
    const turns = [
      { speaker: 'Alice', text: 'Hello' },
      { speaker: 'Bob', text: 'Hi there' }
    ];
    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.DIALOGUE_ANNOTATED,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:04:00Z',
      completedAt: null,
      availableActions: ['configure-output']
    };

    service.annotateDialogue(sessionId, turns).subscribe(result => {
      expect(result.state).toBe(WorkflowState.DIALOGUE_ANNOTATED);
      done();
    });

    const req = httpMock.expectOne(`/api/audiobooks/workflows/${sessionId}/annotate-dialogue`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ turns });
    req.flush(mockResponse);
  });

  it('should POST to /api/audiobooks/workflows/{sessionId}/configure-output with config parameters', (done) => {
    const sessionId = 'session-1';
    const languageCode = 'en-US';
    const modelName = 'gpt-4';
    const audioEncoding = 'mp3';
    const voiceAssignments = new Map([
      ['Alice', 'voice-1'],
      ['Bob', 'voice-2']
    ]);

    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.CONFIGURED,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:05:00Z',
      completedAt: null,
      availableActions: ['generate-audio']
    };

    service.configureOutput(sessionId, languageCode, modelName, audioEncoding, voiceAssignments)
      .subscribe(result => {
        expect(result.state).toBe(WorkflowState.CONFIGURED);
        done();
      });

    const req = httpMock.expectOne(`/api/audiobooks/workflows/${sessionId}/configure-output`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.languageCode).toBe(languageCode);
    expect(req.request.body.modelName).toBe(modelName);
    expect(req.request.body.audioEncoding).toBe(audioEncoding);
    req.flush(mockResponse);
  });

  it('should POST to /api/audiobooks/workflows/{sessionId}/generate-audio', (done) => {
    const sessionId = 'session-1';
    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.RENDERING_AUDIO,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:06:00Z',
      completedAt: null,
      availableActions: []
    };

    service.generateAudio(sessionId).subscribe(result => {
      expect(result.state).toBe(WorkflowState.RENDERING_AUDIO);
      done();
    });

    const req = httpMock.expectOne(`/api/audiobooks/workflows/${sessionId}/generate-audio`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(mockResponse);
  });

  it('should use shareReplay(1) to prevent duplicate HTTP requests on multiple subscribers', () => {
    const sessionId = 'session-1';
    const mockResponse: WorkflowSessionResponse = {
      id: sessionId,
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.DRAFT,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:00:00Z',
      completedAt: null,
      availableActions: []
    };

    const observable = service.getWorkflow(sessionId);

    observable.subscribe();
    observable.subscribe();

    const requests = httpMock.match(`/api/audiobooks/workflows/${sessionId}`);
    expect(requests.length).toBe(1);
    requests[0].flush(mockResponse);
  });

  it('should emit new session state to session$ observable when workflow transitions', (done) => {
    const storyText = 'Test story';
    const mockResponse: WorkflowSessionResponse = {
      id: 'session-1',
      projectId: 'project-1',
      userId: 'user-1',
      state: WorkflowState.DRAFT,
      createdAt: '2026-05-10T10:00:00Z',
      updatedAt: '2026-05-10T10:00:00Z',
      completedAt: null,
      availableActions: []
    };

    const emittedSessions: (WorkflowSessionResponse | null)[] = [];
    service.session$.subscribe(session => {
      emittedSessions.push(session);
    });

    service.createWorkflow(storyText).subscribe(() => {
      expect(emittedSessions.length).toBe(2);
      expect(emittedSessions[0]).toBeNull();
      expect(emittedSessions[1]).toEqual(mockResponse);
      done();
    });

    const req = httpMock.expectOne('/api/audiobooks/workflows');
    req.flush(mockResponse);
  });
});
