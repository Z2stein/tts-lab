import { TestBed } from '@angular/core/testing';
import { LoggerService } from './logger.service';

describe('LoggerService', () => {
  let service: LoggerService;
  let warnSpy: jasmine.Spy;
  let errorSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LoggerService);
    warnSpy = spyOn(console, 'warn');
    errorSpy = spyOn(console, 'error');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('info', () => {
    it('should call console.warn with formatted prefix and message', () => {
      service.info('test', 'test message');
      expect(warnSpy).toHaveBeenCalledWith('[test] test message', undefined);
    });

    it('should include data if provided', () => {
      const data = { key: 'value' };
      service.info('test', 'test message', data);
      expect(warnSpy).toHaveBeenCalledWith('[test] test message', data);
    });
  });

  describe('warn', () => {
    it('should call console.warn with formatted prefix and message', () => {
      service.warn('test', 'test warning');
      expect(warnSpy).toHaveBeenCalledWith('[test] test warning', undefined);
    });

    it('should include data if provided', () => {
      const data = { key: 'value' };
      service.warn('test', 'test warning', data);
      expect(warnSpy).toHaveBeenCalledWith('[test] test warning', data);
    });
  });

  describe('error', () => {
    it('should call console.error with formatted prefix and message', () => {
      service.error('test', 'test error');
      expect(errorSpy).toHaveBeenCalledWith('[test] test error', undefined);
    });

    it('should include data if provided', () => {
      const data = { error: 'details' };
      service.error('test', 'test error', data);
      expect(errorSpy).toHaveBeenCalledWith('[test] test error', data);
    });
  });
});
