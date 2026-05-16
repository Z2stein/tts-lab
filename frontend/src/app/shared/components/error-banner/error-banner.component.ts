import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppErrorBannerService } from '../../services/app-error-banner.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-error-banner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './error-banner.component.html',
  styleUrl: './error-banner.component.scss'
})
export class ErrorBannerComponent implements OnInit, OnDestroy {
  error$ = this.errorBannerService.error$;
  private autoDismissSubscription: Subscription | null = null;

  constructor(private readonly errorBannerService: AppErrorBannerService) {}

  ngOnInit(): void {
    this.error$.subscribe(error => {
      if (error) {
        this.scheduleAutoDismiss();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.autoDismissSubscription) {
      this.autoDismissSubscription.unsubscribe();
    }
  }

  dismissError(): void {
    this.errorBannerService.clearError();
  }

  private scheduleAutoDismiss(): void {
    if (this.autoDismissSubscription) {
      this.autoDismissSubscription.unsubscribe();
    }
    // Auto-dismiss after 8 seconds
    const timeoutId = setTimeout(() => {
      this.errorBannerService.clearError();
    }, 8000);

    this.autoDismissSubscription = new Subscription(() => {
      clearTimeout(timeoutId);
    });
  }
}
