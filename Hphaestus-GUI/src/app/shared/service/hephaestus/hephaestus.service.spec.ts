import { TestBed } from '@angular/core/testing';

import { HephaestusService } from './hephaestus.service';

describe('HephaestusService', () => {
  let service: HephaestusService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(HephaestusService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
