import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdvancedViewComponent } from './advanced-view.component';

describe('AdvancedViewComponent', () => {
  let component: AdvancedViewComponent;
  let fixture: ComponentFixture<AdvancedViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AdvancedViewComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AdvancedViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
