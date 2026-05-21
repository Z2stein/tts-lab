import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AddSpeakerFormComponent } from './add-speaker-form.component';
import { SpeakerVoiceAnalysisItem } from '../../../../audiobook-shared/service/audiobook-workflow.service';

describe('AddSpeakerFormComponent', () => {
  let fixture: ComponentFixture<AddSpeakerFormComponent>;
  let component: AddSpeakerFormComponent;

  function setup(draft?: Partial<SpeakerVoiceAnalysisItem>): void {
    TestBed.configureTestingModule({ imports: [AddSpeakerFormComponent] });
    fixture = TestBed.createComponent(AddSpeakerFormComponent);
    component = fixture.componentInstance;
    component.draft = { speakerName: '', roleDescription: '', voiceSuggestion: '', ...draft };
    fixture.detectChanges();
  }

  it('emits save when a name is present', () => {
    setup({ speakerName: 'Aria' });
    const saveSpy = spyOn(component.save, 'emit');

    fixture.debugElement.query(By.css('[data-testid="add-speaker-save"]')).nativeElement.click();

    expect(saveSpy).toHaveBeenCalledOnceWith();
    expect(component.validationError).toBeNull();
  });

  it('blocks save and shows a validation message when the name is blank', () => {
    setup({ speakerName: '   ' });
    const saveSpy = spyOn(component.save, 'emit');

    fixture.debugElement.query(By.css('[data-testid="add-speaker-save"]')).nativeElement.click();
    fixture.detectChanges();

    expect(saveSpy).not.toHaveBeenCalled();
    const message = fixture.debugElement.query(By.css('[data-testid="add-speaker-validation"]'));
    expect(message.nativeElement.textContent).toContain('Speaker name is required.');
  });

  it('emits pickVoice when choosing a voice', () => {
    setup();
    const pickSpy = spyOn(component.pickVoice, 'emit');

    fixture.debugElement.query(By.css('[data-testid="add-speaker-choose-voice"]')).nativeElement.click();

    expect(pickSpy).toHaveBeenCalledOnceWith();
  });

  it('emits cancel and clears any validation error', () => {
    setup();
    component.validationError = 'Speaker name is required.';
    const cancelSpy = spyOn(component.cancel, 'emit');

    component.onCancel();

    expect(cancelSpy).toHaveBeenCalledOnceWith();
    expect(component.validationError).toBeNull();
  });

  it('shows the selected voice name', () => {
    setup({ voiceSuggestion: 'KORE' });
    const voice = fixture.debugElement.query(By.css('[data-testid="add-speaker-voice"]'));
    expect(voice.nativeElement.textContent).toContain('KORE');
  });
});
