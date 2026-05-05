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
  private csrfToken: string | null = null;

  async getCurrentUser(): Promise<CurrentUser | null> {
    console.info('[auth] Checking current user via /api/me');

    try {
      const response = await fetch('/api/me', { redirect: 'follow' });

      if (response.status === 401) {
        console.info('[auth] /api/me returned 401 (unauthenticated)');
        return null;
      }

      if (response.redirected) {
        console.warn('[auth] /api/me triggered redirect, treating as unauthenticated', {
          redirectedUrl: response.url
        });
        return null;
      }

      const contentType = response.headers.get('content-type') ?? '';
      if (!response.ok || !contentType.includes('application/json')) {
        console.error('[auth] /api/me returned unexpected response', {
          status: response.status,
          contentType
        });
        return null;
      }

      const user = (await response.json()) as CurrentUser;
      console.info('[auth] User authenticated', { id: user.id, authMode: user.authMode });
      return user;
    } catch (error) {
      console.error('[auth] /api/me request failed, treating as unauthenticated', error);
      return null;
    }
  }

  startGoogleLogin(): void {
    console.info('[auth] Starting Google login redirect');
    window.location.href = '/oauth2/authorization/google';
  }

  async startLogout(): Promise<void> {
    try {
      const csrfToken = await this.ensureCsrfToken();
      await fetch('/logout', {
        method: 'POST',
        headers: {
          'X-XSRF-TOKEN': csrfToken
        }
      });
      console.info('[auth] Logout request completed, reloading page');
      window.location.href = '/';
    } catch (error) {
      console.error('[auth] Logout request failed', error);
    }
  }

  async ensureCsrfToken(): Promise<string> {
    if (this.csrfToken) {
      return this.csrfToken;
    }

    let token = this.readCookie('XSRF-TOKEN');
    if (!token) {
      await fetch('/api/me', { redirect: 'follow' });
      token = this.readCookie('XSRF-TOKEN');
    }

    if (!token) {
      throw new Error('Missing CSRF token cookie');
    }

    this.csrfToken = token;
    return token;
  }

  private readCookie(name: string): string | null {
    const prefix = `${name}=`;
    const cookie = document.cookie
      .split(';')
      .map((entry) => entry.trim())
      .find((entry) => entry.startsWith(prefix));

    return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
  }
}
