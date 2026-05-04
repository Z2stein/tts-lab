import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CurrentUser, CurrentUserService } from './current-user.service';
import { TextLengthService } from './text-length.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  textControl = new FormControl('', { nonNullable: true });
  length: number | null = null;
  error: string | null = null;
  currentUser: CurrentUser | null = null;
  isLoadingUser = true;

  constructor(
    private readonly textLengthService: TextLengthService,
    private readonly currentUserService: CurrentUserService
  ) {}

  async ngOnInit(): Promise<void> {
    try {
      this.currentUser = await this.currentUserService.getCurrentUser();
    } finally {
      this.isLoadingUser = false;
    }
  }

  async submit(): Promise<void> {
    this.error = null;
    this.length = null;

    try {
      this.length = await this.textLengthService.getLength(this.textControl.value);
    } catch (e) {
      this.error = e instanceof Error ? e.message : 'Unknown error.';
    }
  }

  loginWithGoogle(): void {
    this.currentUserService.startGoogleLogin();
  }

  logout(): void {
    this.currentUserService.startLogout();
  }
}
