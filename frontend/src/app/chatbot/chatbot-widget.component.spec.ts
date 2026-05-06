import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatbotWidgetComponent } from './chatbot-widget.component';
import { ChatbotService } from './chatbot.service';

describe('ChatbotWidgetComponent', () => {
  let fixture: ComponentFixture<ChatbotWidgetComponent>;
  let component: ChatbotWidgetComponent;
  let chatbotService: jasmine.SpyObj<ChatbotService>;

  beforeEach(async () => {
    chatbotService = jasmine.createSpyObj('ChatbotService', ['sendMessage']);
    await TestBed.configureTestingModule({
      imports: [ChatbotWidgetComponent],
      providers: [{ provide: ChatbotService, useValue: chatbotService }]
    }).compileComponents();
    fixture = TestBed.createComponent(ChatbotWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders closed state', () => {
    expect(component.isOpen).toBeFalse();
    expect(fixture.nativeElement.querySelector('.chatbox')).toBeNull();
  });

  it('opens when user clicks chat button', () => {
    fixture.nativeElement.querySelector('.chat-toggle').click();
    fixture.detectChanges();
    expect(component.isOpen).toBeTrue();
  });

  it('prevents empty message send', async () => {
    component.input = '   ';
    await component.send();
    expect(chatbotService.sendMessage).not.toHaveBeenCalled();
  });

  it('sends message and displays user/assistant response', async () => {
    chatbotService.sendMessage.and.resolveTo({ answer: 'hello back', conversationId: 'c1' });
    component.input = 'hello';
    await component.send();
    expect(component.messages[0].text).toBe('hello');
    expect(component.messages[1].text).toBe('hello back');
  });

  it('shows loading state', async () => {
    let resolveFn: any;
    chatbotService.sendMessage.and.returnValue(new Promise(resolve => (resolveFn = resolve)));
    component.input = 'hello';
    const p = component.send();
    expect(component.loading).toBeTrue();
    resolveFn({ answer: 'done', conversationId: 'c1' });
    await p;
  });

  it('shows error state', async () => {
    chatbotService.sendMessage.and.rejectWith(new Error('fail'));
    component.input = 'hello';
    await component.send();
    expect(component.error).toBe('fail');
  });
});
