import { ChatbotService } from './chatbot.service';

describe('ChatbotService', () => {
  it('sends POST request to /api/chat and handles success', async () => {
    const ensureCsrfToken = jasmine.createSpy().and.resolveTo('csrf-token');
    const service = new ChatbotService({ ensureCsrfToken } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({ answer: 'ok', conversationId: 'c1' }), { status: 200 }));

    const res = await service.sendMessage('hello', null);

    expect(window.fetch).toHaveBeenCalledWith('/api/chat', jasmine.objectContaining({ method: 'POST' }));
    expect(res.answer).toBe('ok');
  });

  it('handles backend error', async () => {
    const service = new ChatbotService({ ensureCsrfToken: async () => 'csrf' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response('{}', { status: 500 }));

    await expectAsync(service.sendMessage('hello', null)).toBeRejected();
  });

  it('maps rate-limit response to a user-facing message', async () => {
    const service = new ChatbotService({ ensureCsrfToken: async () => 'csrf' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify({ error: 'RATE_LIMIT_EXCEEDED', retry_after: 42 }), { status: 429 }));

    await expectAsync(service.sendMessage('hello', null)).toBeRejectedWithError('Chat rate limit reached. Please try again in 42 seconds.');
  });

});
