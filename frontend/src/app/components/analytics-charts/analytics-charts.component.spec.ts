import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnalyticsChartsComponent } from './analytics-charts.component';

describe('AnalyticsChartsComponent', () => {
  let component: AnalyticsChartsComponent;
  let fixture: ComponentFixture<AnalyticsChartsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsChartsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsChartsComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
