import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { CurrentUser, CurrentUserService } from './current-user.service';

describe('AppComponent chatbot visibility', () => {
  let fixture: ComponentFixture<AppComponent>;
  let currentUserService: jasmine.SpyObj<CurrentUserService>;

  beforeEach(async () => {
    currentUserService = jasmine.createSpyObj<CurrentUserService>('CurrentUserService', [
      'getCurrentUser',
      'ensureCsrfToken',
      'startGoogleLogin',
      'startLogout'
    ]);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter(routes),
        { provide: CurrentUserService, useValue: currentUserService }
      ]
    }).compileComponents();
  });

  it('chatbot widget is not visible when unauthenticated', async () => {
    currentUserService.getCurrentUser.and.resolveTo(null);
    fixture = TestBed.createComponent(AppComponent);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chatbot-widget')).toBeNull();
  });

  it('chatbot widget is visible when authenticated', async () => {
    const user: CurrentUser = { id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] };
    currentUserService.getCurrentUser.and.resolveTo(user);
    fixture = TestBed.createComponent(AppComponent);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chatbot-widget')).not.toBeNull();
  });
});
