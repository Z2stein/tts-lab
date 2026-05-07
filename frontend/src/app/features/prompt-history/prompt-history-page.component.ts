import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { PromptHistoryItem, PromptHistoryService, PromptModelType } from './prompt-history.service';

@Component({
  selector: 'app-prompt-history-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './prompt-history-page.component.html',
  styleUrl: './prompt-history-page.component.css'
})
export class PromptHistoryPageComponent implements OnInit {
  modelTypeControl = new FormControl<'ALL' | PromptModelType>('ALL', { nonNullable: true });
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
