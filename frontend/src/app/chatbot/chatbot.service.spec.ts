import { ChatbotService } from './chatbot.service';
import { loadTestContractJson } from '../shared/test-contracts';

describe('ChatbotService', () => {
  it('sends POST request to /api/chat and handles success', async () => {
    const ensureCsrfToken = jasmine.createSpy().and.resolveTo('csrf-token');
    const service = new ChatbotService({ ensureCsrfToken } as any);
    const response = await loadTestContractJson<{ answer: string; conversationId: string }>('chat/success/response.json');
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify(response), { status: 200 }));

    const res = await service.sendMessage('hello', null);

    expect(window.fetch).toHaveBeenCalledWith('/api/chat', jasmine.objectContaining({ method: 'POST' }));
    expect(res.answer).toBe('hello');
  });

  it('handles backend error', async () => {
    const service = new ChatbotService({ ensureCsrfToken: async () => 'csrf' } as any);
    spyOn(window, 'fetch').and.resolveTo(new Response('{}', { status: 500 }));

    await expectAsync(service.sendMessage('hello', null)).toBeRejected();
  });

  it('maps rate-limit response to a user-facing message', async () => {
    const service = new ChatbotService({ ensureCsrfToken: async () => 'csrf' } as any);
    const response = await loadTestContractJson<{ message: string }>('chat/rate-limited/response.json');
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify(response), { status: 429 }));

    await expectAsync(service.sendMessage('hello', null)).toBeRejectedWithError('Usage limit exceeded. Please try again later.');
  });

});
