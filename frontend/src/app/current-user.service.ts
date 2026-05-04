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
    const response = await fetch('/api/me');
    if (response.status === 401) {
      return null;
    }
    if (!response.ok) {
      throw new Error('Failed to load current user.');
    }
    return (await response.json()) as CurrentUser;
  }
}
