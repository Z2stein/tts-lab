import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { CurrentUser, CurrentUserService } from './current-user.service';
import { routes } from './app.routes';

describe('AppComponent layout and chatbot visibility', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let currentUserService: jasmine.SpyObj<CurrentUserService>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        {
          provide: CurrentUserService,
          useValue: {
            getCurrentUser: jasmine.createSpy(),
            refreshRequestLimits: jasmine.createSpy().and.resolveTo(null),
            startGoogleLogin: jasmine.createSpy(),
            startLogout: jasmine.createSpy()
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    currentUserService = TestBed.inject(CurrentUserService) as jasmine.SpyObj<CurrentUserService>;
  });

  beforeEach(() => {
    currentUserService.getCurrentUser.and.resolveTo({ id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] });
  });

  async function renderAuthenticatedShell(user: CurrentUser = { id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] }): Promise<void> {
    currentUserService.getCurrentUser.and.resolveTo(user);
    component.authStatus = 'authenticated';
    component.currentUser = user;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows navigation links in the shared header and keeps prompt history hidden for non-owner accounts', async () => {
    await renderAuthenticatedShell();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('TTS Lab');
    expect(text).toContain('Audiobook Studio');
    expect(text).toContain('My Audiobooks');
    expect(text).toContain('Demo');
    expect(text).not.toContain('Prompt History');
  });

  it('shows the account menu trigger for authenticated users', async () => {
    await renderAuthenticatedShell({ id: '1', email: 'u@test.dev', name: 'Chris Th', authMode: 'mock', roles: ['USER'] });

    const trigger = fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]');
    expect(trigger).not.toBeNull();
    expect(trigger.textContent).toContain('CT');
  });

  it('shows prompt history for the owner account', async () => {
    await renderAuthenticatedShell({ id: '1', email: 'christiophthurn0@gmail.com', name: 'Owner', authMode: 'mock', roles: ['USER'] });

    expect(fixture.nativeElement.textContent).toContain('Prompt History');
  });

  it('delegates logout through the app shell when the account menu emits', async () => {
    currentUserService.startLogout.and.resolveTo();
    await renderAuthenticatedShell({ id: '1', email: 'u@test.dev', name: 'Chris Th', authMode: 'mock', roles: ['USER'] });

    fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="account-menu-logout"]').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(currentUserService.startLogout).toHaveBeenCalled();
  });

  it('chatbot widget is not visible when unauthenticated', () => {
    component.authStatus = 'unauthenticated';
    component.currentUser = null;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chatbot-widget')).toBeNull();
  });

  it('chatbot widget is visible when authenticated', async () => {
    component.authStatus = 'authenticated';
    component.currentUser = { id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] };
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chatbot-widget')).not.toBeNull();
  });
});
