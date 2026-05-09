import { TestBed } from '@angular/core/testing';
import { VoiceSampleService } from './voice-sample.service';

describe('VoiceSampleService', () => {
  let service: VoiceSampleService;
  let mockAudio: jasmine.SpyObj<HTMLAudioElement> & { paused: boolean };
  let eventListeners: Record<string, EventListenerOrEventListenerObject[]>;

  beforeEach(() => {
    eventListeners = {};
    mockAudio = {
      ...jasmine.createSpyObj<HTMLAudioElement>('HTMLAudioElement', ['play', 'pause', 'addEventListener']),
      paused: false,
    };
    mockAudio.play.and.returnValue(Promise.resolve());
    mockAudio.addEventListener.and.callFake((type: string, listener: EventListenerOrEventListenerObject) => {
      if (!eventListeners[type]) eventListeners[type] = [];
      eventListeners[type].push(listener);
    });

    spyOn(window, 'Audio' as never).and.returnValue(mockAudio as never);

    TestBed.configureTestingModule({ providers: [VoiceSampleService] });
    service = TestBed.inject(VoiceSampleService);
  });

  it('sets activeSampleKey when starting playback', () => {
    service.play('/audio.mp3', 'voice:Narrator');
    expect(service.activeSampleKey).toBe('voice:Narrator');
  });

  it('pauses and clears activeSampleKey when the same key is played again', () => {
    service.play('/audio.mp3', 'voice:Narrator');
    service.play('/audio.mp3', 'voice:Narrator');
    expect(mockAudio.pause).toHaveBeenCalledTimes(1);
    expect(service.activeSampleKey).toBeNull();
  });

  it('pauses previous audio when a different key is played', () => {
    service.play('/a.mp3', 'voice:Narrator');
    service.play('/b.mp3', 'voice:Mara');
    expect(mockAudio.pause).toHaveBeenCalledTimes(1);
    expect(service.activeSampleKey).toBe('voice:Mara');
  });

  it('clears activeSampleKey on the ended event', () => {
    service.play('/audio.mp3', 'voice:Narrator');
    (eventListeners['ended']?.[0] as EventListener)?.({ type: 'ended' } as Event);
    expect(service.activeSampleKey).toBeNull();
  });

  it('clears activeSampleKey on the pause event when audio is paused', () => {
    service.play('/audio.mp3', 'voice:Narrator');
    mockAudio.paused = true;
    (eventListeners['pause']?.[0] as EventListener)?.({ type: 'pause' } as Event);
    expect(service.activeSampleKey).toBeNull();
  });

  it('calls onPlayError and clears key when play() rejects', async () => {
    mockAudio.play.and.returnValue(Promise.reject(new Error('blocked')));
    const onPlayError = jasmine.createSpy('onPlayError');
    service.play('/audio.mp3', 'voice:Narrator', onPlayError);
    await Promise.resolve(); // flush the rejection
    expect(onPlayError).toHaveBeenCalledTimes(1);
    expect(service.activeSampleKey).toBeNull();
  });

  it('pauses and clears on destroy', () => {
    service.play('/audio.mp3', 'voice:Narrator');
    service.destroy();
    expect(mockAudio.pause).toHaveBeenCalledTimes(1);
    expect(service.activeSampleKey).toBeNull();
  });

  it('pause() stops the current audio', () => {
    service.play('/audio.mp3', 'voice:Narrator');
    service.pause();
    expect(mockAudio.pause).toHaveBeenCalledTimes(1);
  });
});
