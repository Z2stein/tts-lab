import { Injectable } from '@angular/core';

export type CurrentUser = {
  id: string;
  email: string;
  name: string;
  roles: string[];
  authMode: 'google' | 'mock';
};

@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  async getCurrentUser(): Promise<CurrentUser | null> {
    const response = await fetch('/api/me', { redirect: 'follow' });
    if (response.status === 401 || response.redirected) {
      return null;
    }

    const contentType = response.headers.get('content-type') ?? '';
    if (!response.ok || !contentType.includes('application/json')) {
      return null;
    }

    return (await response.json()) as CurrentUser;
  }

  startGoogleLogin(): void {
    window.location.href = '/oauth2/authorization/google';
  }
}
