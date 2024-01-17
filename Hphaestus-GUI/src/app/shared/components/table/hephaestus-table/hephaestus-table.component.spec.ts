import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HephaestusTableComponent } from './hephaestus-table.component';

describe('HephaestusTableComponent', () => {
  let component: HephaestusTableComponent;
  let fixture: ComponentFixture<HephaestusTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ HephaestusTableComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HephaestusTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
