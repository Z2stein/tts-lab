import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AccountMenuComponent } from './account-menu.component';
import { CurrentUser } from './current-user.service';

describe('AccountMenuComponent', () => {
  let fixture: ComponentFixture<AccountMenuComponent>;
  let component: AccountMenuComponent;

  const currentUser: CurrentUser = {
    id: '1',
    email: 'christiophthurn0@gmail.com',
    name: 'Chris Th',
    authMode: 'google',
    roles: ['USER']
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountMenuComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AccountMenuComponent);
    component = fixture.componentInstance;
    component.currentUser = currentUser;
    fixture.detectChanges();
  });

  it('shows derived initials in the trigger', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]').textContent).toContain('CT');
  });

  it('reveals the user name and provider copy when opened', () => {
    fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]').click();
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('[data-testid="account-menu-panel"]');
    expect(panel.textContent).toContain('Chris Th');
    expect(panel.textContent).toContain('Signed in with Google');
  });

  it('emits logout and closes the menu when logout is clicked', () => {
    const logoutSpy = spyOn(component.logout, 'emit');

    fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="account-menu-logout"]').click();
    fixture.detectChanges();

    expect(logoutSpy).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="account-menu-panel"]')).toBeNull();
  });

  it('closes when clicking outside the menu', () => {
    component.isOpen = true;
    fixture.detectChanges();

    document.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(component.isOpen).toBeFalse();
  });

  it('closes when escape is pressed', () => {
    component.isOpen = true;
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(component.isOpen).toBeFalse();
  });

  it('keeps the menu open when clicking inside the component', () => {
    component.isOpen = true;
    fixture.detectChanges();

    fixture.debugElement.query(By.css('[data-testid="account-menu-panel"]')).nativeElement
      .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(component.isOpen).toBeTrue();
  });
});
