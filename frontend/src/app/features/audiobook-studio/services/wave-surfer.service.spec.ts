import { TestBed } from '@angular/core/testing';
import WaveSurfer from 'wavesurfer.js';
import { WaveSurferService } from './wave-surfer.service';

describe('WaveSurferService', () => {
  let service: WaveSurferService;
  let createSpy: jasmine.Spy;

  function makeMockWs(): jasmine.SpyObj<WaveSurfer> {
    return jasmine.createSpyObj<WaveSurfer>('WaveSurfer', [
      'destroy', 'pause', 'isPlaying', 'on',
    ]);
  }

  beforeEach(() => {
    const mock = makeMockWs();
    createSpy = spyOn(WaveSurfer, 'create').and.returnValue(mock);

    TestBed.configureTestingModule({ providers: [WaveSurferService] });
    service = TestBed.inject(WaveSurferService);
  });

  it('creates an instance, registers it, and returns it', () => {
    const container = document.createElement('div');
    const ws = service.create('demo', container, '/audio.mp3');
    expect(createSpy).toHaveBeenCalled();
    expect(service.get('demo')).toBe(ws);
  });

  it('returns null for an unknown key', () => {
    expect(service.get('unknown')).toBeNull();
  });

  it('destroys a single registered instance', () => {
    const mock = makeMockWs();
    createSpy.and.returnValue(mock);
    const container = document.createElement('div');
    service.create('demo', container, '/audio.mp3');
    service.destroy('demo');
    expect(mock.destroy).toHaveBeenCalledTimes(1);
    expect(service.get('demo')).toBeNull();
  });

  it('destroys all registered instances', () => {
    const mockA = makeMockWs();
    const mockB = makeMockWs();
    createSpy.and.returnValues(mockA, mockB);
    const container = document.createElement('div');
    service.create('a', container, '/a.mp3');
    service.create('b', container, '/b.mp3');
    service.destroyAll();
    expect(mockA.destroy).toHaveBeenCalledTimes(1);
    expect(mockB.destroy).toHaveBeenCalledTimes(1);
    expect(service.get('a')).toBeNull();
    expect(service.get('b')).toBeNull();
  });

  it('pauseAll pauses playing instances except the excluded one', () => {
    const mockA = makeMockWs();
    const mockB = makeMockWs();
    createSpy.and.returnValues(mockA, mockB);
    mockA.isPlaying.and.returnValue(true);
    mockB.isPlaying.and.returnValue(true);
    const container = document.createElement('div');
    service.create('a', container, '/a.mp3');
    const wsB = service.create('b', container, '/b.mp3');
    service.pauseAll(wsB);
    expect(mockA.pause).toHaveBeenCalledTimes(1);
    expect(mockB.pause).not.toHaveBeenCalled();
  });

  it('pauseAll does not pause instances that are not playing', () => {
    const mock = makeMockWs();
    createSpy.and.returnValue(mock);
    mock.isPlaying.and.returnValue(false);
    const container = document.createElement('div');
    service.create('demo', container, '/audio.mp3');
    service.pauseAll();
    expect(mock.pause).not.toHaveBeenCalled();
  });

  it('bind wires play/pause/finish callbacks', () => {
    const mock = makeMockWs();
    const callbacks: Record<string, Function> = {};
    mock.on.and.callFake((event: string, cb: Function) => { callbacks[event] = cb; return () => {}; });

    const onPlay = jasmine.createSpy('onPlay');
    const onStateChange = jasmine.createSpy('onStateChange');
    service.bind(mock, onPlay, onStateChange);

    callbacks['play']?.();
    expect(onPlay).toHaveBeenCalledTimes(1);
    expect(onStateChange).toHaveBeenCalledWith(true);

    callbacks['pause']?.();
    expect(onStateChange).toHaveBeenCalledWith(false);

    callbacks['finish']?.();
    expect(onStateChange).toHaveBeenCalledWith(false);
  });
});
