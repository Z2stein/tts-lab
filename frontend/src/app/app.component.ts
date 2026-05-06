import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ChatbotWidgetComponent } from './chatbot/chatbot-widget.component';
import { CurrentUser, CurrentUserService } from './current-user.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, ChatbotWidgetComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  authStatus: 'loading' | 'authenticated' | 'unauthenticated' = 'loading';
  authError: string | null = null;
  currentUser: CurrentUser | null = null;

  constructor(private readonly currentUserService: CurrentUserService) {}

  async ngOnInit(): Promise<void> {
    console.info('[app] Initializing app and resolving auth state');
    try {
      this.currentUser = await this.currentUserService.getCurrentUser();
      this.authStatus = this.currentUser ? 'authenticated' : 'unauthenticated';
    } catch (error) {
      console.error('[app] Unexpected auth initialization error', error);
      this.authStatus = 'unauthenticated';
      this.authError = 'Could not validate session. Please try signing in again.';
    }
    console.info('[app] Auth state resolved', { authStatus: this.authStatus });
  }

  loginWithGoogle(): void {
    this.currentUserService.startGoogleLogin();
  }

  async logout(): Promise<void> {
    await this.currentUserService.startLogout();
  }
}
