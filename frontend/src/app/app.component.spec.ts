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

  it('shows navigation links in the shared header', () => {
    component.authStatus = 'authenticated';
    component.currentUser = { id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] };
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Home');
    expect(text).toContain('Text Length');
    expect(text).toContain('TTS Workbench');
  });

  it('chatbot widget is not visible when unauthenticated', () => {
    component.authStatus = 'unauthenticated';
    component.currentUser = null;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chatbot-widget')).toBeNull();
  });

  it('chatbot widget is visible when authenticated', () => {
    component.authStatus = 'authenticated';
    component.currentUser = { id: '1', email: 'u@test.dev', name: 'User', authMode: 'mock', roles: ['USER'] };
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chatbot-widget')).not.toBeNull();
  });
});
