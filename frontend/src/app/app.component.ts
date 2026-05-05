import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CurrentUser, CurrentUserService } from './current-user.service';
import { TextLengthService } from './text-length.service';
import { ChatbotWidgetComponent } from './chatbot/chatbot-widget.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ChatbotWidgetComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  authStatus: 'loading' | 'authenticated' | 'unauthenticated' = 'loading';
  authError: string | null = null;
  textControl = new FormControl('', { nonNullable: true });
  length: number | null = null;
  error: string | null = null;
  currentUser: CurrentUser | null = null;

  constructor(
    private readonly textLengthService: TextLengthService,
    private readonly currentUserService: CurrentUserService
  ) {}

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

  async submit(): Promise<void> {
    this.error = null;
    this.length = null;

    try {
      this.length = await this.textLengthService.getLength(this.textControl.value);
    } catch (e) {
      this.error = e instanceof Error ? e.message : 'Unknown error.';
      console.error('[app] Text length request failed', e);
    }
  }

  loginWithGoogle(): void {
    this.currentUserService.startGoogleLogin();
  }

  async logout(): Promise<void> {
    await this.currentUserService.startLogout();
  }
}
