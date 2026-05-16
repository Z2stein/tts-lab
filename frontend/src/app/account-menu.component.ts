import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { CurrentUser } from './current-user.service';

@Component({
  selector: 'app-account-menu',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './account-menu.component.html'
})
export class AccountMenuComponent {
  @Input({ required: true }) currentUser!: CurrentUser;
  @Output() logout = new EventEmitter<void>();

  isOpen = false;

  constructor(private readonly elementRef: ElementRef<HTMLElement>) {}

  get initials(): string {
    const parts = this.currentUser.name
      .trim()
      .split(/\s+/)
      .filter(Boolean);

    if (parts.length === 0) {
      return '?';
    }

    return parts
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }

  get providerLabel(): string {
    return this.currentUser.authMode === 'google'
      ? 'Signed in with Google'
      : 'Signed in with Mock';
  }

  toggleMenu(): void {
    this.isOpen = !this.isOpen;
  }

  closeMenu(): void {
    this.isOpen = false;
  }

  onLogout(): void {
    this.closeMenu();
    this.logout.emit();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen) {
      return;
    }

    const target = event.target;
    if (!(target instanceof Node)) {
      return;
    }

    if (!this.elementRef.nativeElement.contains(target)) {
      this.closeMenu();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closeMenu();
  }
}
