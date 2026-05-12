import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { CurrentUserService } from './current-user.service';
import { routes } from './app.routes';

describe('AppComponent layout and chatbot visibility', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter(routes),
        {
          provide: CurrentUserService,
          useValue: {
            getCurrentUser: jasmine.createSpy(),
            refreshRequestLimits: jasmine.createSpy().and.resolveTo(null),
            ensureCsrfToken: jasmine.createSpy(),
            startGoogleLogin: jasmine.createSpy(),
            startLogout: jasmine.createSpy()
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
  });

  beforeEach(() => {
    const currentUserService = TestBed.inject(CurrentUserService) as jasmine.SpyObj<CurrentUserService>;
    currentUserService.getCurrentUser.and.resolveTo({ id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] });
  });

  it('shows navigation links in the shared header', () => {
    component.authStatus = 'authenticated';
    component.currentUser = { id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] };
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Home');
    expect(text).toContain('Audiobook Studio');
    expect(text).toContain('My Audiobooks');
    expect(text).toContain('Prompt History');
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
