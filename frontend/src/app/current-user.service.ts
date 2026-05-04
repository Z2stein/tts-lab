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
    try {
      const response = await fetch('/api/me', { redirect: 'follow' });
      if (response.status === 401 || response.redirected) {
        return null;
      }

      const contentType = response.headers.get('content-type') ?? '';
      if (!response.ok || !contentType.includes('application/json')) {
        console.warn('Unexpected /api/me response', {
          status: response.status,
          contentType
        });
        return null;
      }

      return (await response.json()) as CurrentUser;
    } catch (error) {
      console.error('Failed to load current user from /api/me', error);
      return null;
    }
  }

  startGoogleLogin(): void {
    window.location.href = '/oauth2/authorization/google';
  }

  startLogout(): void {
    window.location.href = '/logout';
  }
}
