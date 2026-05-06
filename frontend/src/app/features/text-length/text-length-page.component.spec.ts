import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextLengthPageComponent } from './text-length-page.component';
import { TextLengthService } from './text-length.service';

describe('TextLengthPageComponent', () => {
  let fixture: ComponentFixture<TextLengthPageComponent>;
  let component: TextLengthPageComponent;
  let textLengthService: jasmine.SpyObj<TextLengthService>;

  beforeEach(async () => {
    textLengthService = jasmine.createSpyObj<TextLengthService>('TextLengthService', ['getLength']);

    await TestBed.configureTestingModule({
      imports: [TextLengthPageComponent],
      providers: [{ provide: TextLengthService, useValue: textLengthService }]
    }).compileComponents();

    fixture = TestBed.createComponent(TextLengthPageComponent);
    component = fixture.componentInstance;
  });

  it('submits text and displays the returned length', async () => {
    textLengthService.getLength.and.resolveTo(3);
    component.textControl.setValue('abc');

    await component.submit();
    fixture.detectChanges();

    expect(textLengthService.getLength).toHaveBeenCalledWith('abc');
    expect(fixture.nativeElement.textContent).toContain('Length: 3');
  });

  it('shows an error when the backend request fails', async () => {
    textLengthService.getLength.and.rejectWith(new Error('Backend request failed (HTTP 500).'));

    await component.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Backend request failed (HTTP 500).');
  });
});
