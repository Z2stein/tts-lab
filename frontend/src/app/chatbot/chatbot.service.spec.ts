import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CurrentUserService } from '../current-user.service';
import { ChatbotService } from './chatbot.service';

describe('ChatbotService', () => {
  let service: ChatbotService;
  let httpMock: HttpTestingController;
  let currentUserService: jasmine.SpyObj<CurrentUserService>;

  beforeEach(() => {
    currentUserService = jasmine.createSpyObj<CurrentUserService>('CurrentUserService', ['refreshRequestLimits']);
    currentUserService.refreshRequestLimits.and.resolveTo(null);

    TestBed.configureTestingModule({
      providers: [
        ChatbotService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CurrentUserService, useValue: currentUserService }
      ]
    });

    service = TestBed.inject(ChatbotService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sends POST request to /api/chat and handles success', async () => {
    const promise = service.sendMessage('hello', null);

    const req = httpMock.expectOne('/api/chat');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ message: 'hello', conversationId: null });

    req.flush({ answer: 'hello', conversationId: 'conversation-1' });

    await expectAsync(promise).toBeResolvedTo({ answer: 'hello', conversationId: 'conversation-1' });
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });

  it('handles backend error', async () => {
    const promise = service.sendMessage('hello', null);

    const req = httpMock.expectOne('/api/chat');
    req.flush({ message: 'Chat request failed from backend.' }, { status: 500, statusText: 'Server Error' });

    await expectAsync(promise).toBeRejectedWithError('Chat request failed from backend.');
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });

  it('maps rate-limit response to a user-facing message', async () => {
    const promise = service.sendMessage('hello', null);

    const req = httpMock.expectOne('/api/chat');
    req.flush(
      {
        message: 'Usage limit exceeded. Please try again later.',
        code: 'RATE_LIMIT_EXCEEDED'
      },
      { status: 429, statusText: 'Too Many Requests' }
    );

    await expectAsync(promise).toBeRejectedWithError('Usage limit exceeded. Please try again later.');
    expect(currentUserService.refreshRequestLimits).toHaveBeenCalled();
  });
});
