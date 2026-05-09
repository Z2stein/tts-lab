import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { ChatbotWidgetComponent } from './chatbot/chatbot-widget.component';
import { CurrentUser, CurrentUserService, RequestLimitItem, RequestLimitSummary } from './current-user.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, ChatbotWidgetComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  authStatus: 'loading' | 'authenticated' | 'unauthenticated' = 'loading';
  authError: string | null = null;
  currentUser: CurrentUser | null = null;
  requestLimitSummary: RequestLimitSummary | null = null;
  isPublicRoute = false;
  private authInitialized = false;
  private routerEventsSubscription: Subscription | null = null;
  private readonly requestLimitsUpdated = (event: Event): void => {
    this.requestLimitSummary = (event as CustomEvent<RequestLimitSummary>).detail;
  };

  constructor(
    private readonly currentUserService: CurrentUserService,
    private readonly router: Router
  ) {}

  async ngOnInit(): Promise<void> {
    window.addEventListener('request-limits-updated', this.requestLimitsUpdated);
    this.updatePublicRouteState(this.router.url);
    this.routerEventsSubscription = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updatePublicRouteState(event.urlAfterRedirects);
        if (!this.isPublicRoute && !this.authInitialized) {
          void this.initializeAuth();
        }
      });

    if (!this.isPublicRoute) {
      await this.initializeAuth();
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener('request-limits-updated', this.requestLimitsUpdated);
    this.routerEventsSubscription?.unsubscribe();
  }

  private async initializeAuth(): Promise<void> {
    this.authInitialized = true;
    this.authStatus = 'loading';
    console.info('[app] Initializing app and resolving auth state');
    try {
      this.currentUser = await this.currentUserService.getCurrentUser();
      this.authStatus = this.currentUser ? 'authenticated' : 'unauthenticated';
      if (this.currentUser) {
        this.requestLimitSummary = await this.currentUserService.refreshRequestLimits();
      }
    } catch (error) {
      console.error('[app] Unexpected auth initialization error', error);
      this.authStatus = 'unauthenticated';
      this.authError = 'Could not validate session. Please try signing in again.';
    }
    console.info('[app] Auth state resolved', { authStatus: this.authStatus });
  }

  private updatePublicRouteState(url: string): void {
    const path = url === '/' ? window.location.pathname : url.split('?')[0].split('#')[0];
    this.isPublicRoute = path === '/cv-audiobook-demo';
  }

  loginWithGoogle(): void {
    this.currentUserService.startGoogleLogin();
  }

  async logout(): Promise<void> {
    await this.currentUserService.startLogout();
  }

  limitFor(modelType: 'TEXT_MODEL' | 'SPEECH_MODEL'): RequestLimitItem | null {
    return this.requestLimitSummary?.limits.find((limit) => limit.modelType === modelType) ?? null;
  }

  formatRemaining(limit: RequestLimitItem | null): string {
    if (!limit) {
      return '...';
    }
    return `${limit.remaining}/${limit.limit} ${limit.unit.toLowerCase()}`;
  }
}
