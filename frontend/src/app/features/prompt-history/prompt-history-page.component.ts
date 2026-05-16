import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { PromptHistoryItem, PromptHistoryService } from './prompt-history.service';
import { ModelType } from '../../shared/api-contract.generated';

@Component({
  selector: 'app-prompt-history-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './prompt-history-page.component.html'
})
export class PromptHistoryPageComponent implements OnInit {
  modelTypeControl = new FormControl<'ALL' | ModelType>('ALL', { nonNullable: true });
  prompts: PromptHistoryItem[] = [];
  loading = false;
  error: string | null = null;

  constructor(private readonly promptHistoryService: PromptHistoryService) {}

  async ngOnInit(): Promise<void> {
    await this.loadHistory();
  }

  async loadHistory(): Promise<void> {
    this.loading = true;
    this.error = null;

    try {
      const selectedModelType = this.modelTypeControl.value === 'ALL' ? undefined : this.modelTypeControl.value;
      this.prompts = await this.promptHistoryService.getHistory(selectedModelType);
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Prompt history failed.';
    } finally {
      this.loading = false;
    }
  }

  formatTimestamp(value: string): string {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }
}
