import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ClaimDetailsComponent } from './claim-details.component';

describe('ClaimDetailsComponent', () => {
  let component: ClaimDetailsComponent;
  let fixture: ComponentFixture<ClaimDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimDetailsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ClaimDetailsComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
