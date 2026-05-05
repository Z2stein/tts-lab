import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { TextLengthService } from './text-length.service';
import { CurrentUserService } from './current-user.service';

describe('AppComponent chatbot visibility', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: TextLengthService, useValue: { getLength: jasmine.createSpy().and.resolveTo(3) } },
        { provide: CurrentUserService, useValue: { getCurrentUser: jasmine.createSpy(), ensureCsrfToken: jasmine.createSpy(), startGoogleLogin: jasmine.createSpy(), startLogout: jasmine.createSpy() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
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
