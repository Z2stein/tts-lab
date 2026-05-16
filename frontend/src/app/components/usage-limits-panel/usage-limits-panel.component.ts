import { CommonModule } from '@angular/common';
import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { RequestRateLimitSummary, RequestRateLimitSummaryItem } from '../../shared/api-contract.generated';

@Component({
  selector: 'app-usage-limits-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './usage-limits-panel.component.html',
  styleUrl: './usage-limits-panel.component.css'
})
export class UsageLimitsPanelComponent implements OnInit, OnDestroy {
  @Input() limitSummary: RequestRateLimitSummary | null = null;

  isExpanded = false;
  resetCountdown = '';
  private countdownInterval: number | null = null;

  ngOnInit(): void {
    this.updateCountdown();
    this.countdownInterval = window.setInterval(() => this.updateCountdown(), 1000);
  }

  ngOnDestroy(): void {
    if (this.countdownInterval !== null) {
      clearInterval(this.countdownInterval);
    }
  }

  toggleExpanded(): void {
    this.isExpanded = !this.isExpanded;
  }

  getOverallUsagePercent(): number {
    if (!this.limitSummary?.limits || this.limitSummary.limits.length === 0) {
      return 0;
    }
    const maxPercent = this.limitSummary.limits.reduce((max, limit) => {
      const percent = Math.round((limit.used / limit.limit) * 100);
      return Math.max(max, percent);
    }, 0);
    return maxPercent;
  }

  getProgressPercent(limit: RequestRateLimitSummaryItem): number {
    return Math.round((limit.used / limit.limit) * 100);
  }

  getDisplayName(modelType: string): string {
    switch (modelType) {
      case 'SPEECH_MODEL':
        return 'Speech generation';
      case 'TEXT_MODEL':
        return 'Text analysis';
      default:
        return modelType;
    }
  }

  private updateCountdown(): void {
    if (!this.limitSummary?.windowResetAt) {
      this.resetCountdown = '';
      return;
    }

    const resetTime = new Date(this.limitSummary.windowResetAt).getTime();
    const now = Date.now();
    const diffMs = resetTime - now;

    if (diffMs <= 0) {
      this.resetCountdown = 'Resetting now...';
      return;
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
      this.resetCountdown = `Resets in ${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      this.resetCountdown = `Resets in ${minutes}m ${seconds}s`;
    } else {
      this.resetCountdown = `Resets in ${seconds}s`;
    }
  }
}
