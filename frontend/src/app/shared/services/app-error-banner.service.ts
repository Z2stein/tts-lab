import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface ErrorMessage {
  message: string;
  timestamp: number;
}

@Injectable({
  providedIn: 'root'
})
export class AppErrorBannerService {
  private readonly errorSubject = new BehaviorSubject<ErrorMessage | null>(null);
  public readonly error$: Observable<ErrorMessage | null> = this.errorSubject.asObservable();

  showError(message: string): void {
    this.errorSubject.next({
      message,
      timestamp: Date.now()
    });
  }

  clearError(): void {
    this.errorSubject.next(null);
  }
}
