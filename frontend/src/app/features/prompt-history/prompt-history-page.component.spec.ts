import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PromptHistoryPageComponent } from './prompt-history-page.component';
import { PromptHistoryService } from './prompt-history.service';

describe('PromptHistoryPageComponent', () => {
  let fixture: ComponentFixture<PromptHistoryPageComponent>;
  let component: PromptHistoryPageComponent;
  let promptHistoryService: jasmine.SpyObj<PromptHistoryService>;

  beforeEach(async () => {
    promptHistoryService = jasmine.createSpyObj<PromptHistoryService>('PromptHistoryService', ['getHistory']);
    promptHistoryService.getHistory.and.resolveTo([
      {
        id: 1,
        userId: 'user-1',
        userEmail: 'user1@example.com',
        modelType: 'TEXT_MODEL',
        providerModelName: 'mock',
        promptText: 'Summarize this paragraph.',
        requestStatus: 'SUCCESS',
        createdAt: '2026-05-07T12:00:00Z'
      }
    ]);

    await TestBed.configureTestingModule({
      imports: [PromptHistoryPageComponent],
      providers: [{ provide: PromptHistoryService, useValue: promptHistoryService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PromptHistoryPageComponent);
    component = fixture.componentInstance;
  });

  it('renders prompt history rows', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Prompt History');
    expect(text).toContain('TEXT_MODEL');
    expect(text).toContain('mock');
    expect(text).toContain('Summarize this paragraph.');
  });

  it('filters by model type when changed', async () => {
    component.modelTypeControl.setValue('SPEECH_MODEL');

    await component.loadHistory();

    expect(promptHistoryService.getHistory).toHaveBeenCalledWith('SPEECH_MODEL');
  });
});
