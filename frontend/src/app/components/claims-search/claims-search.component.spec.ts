import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ClaimsSearchComponent } from './claims-search.component';

describe('ClaimsSearchComponent', () => {
  let component: ClaimsSearchComponent;
  let fixture: ComponentFixture<ClaimsSearchComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimsSearchComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ClaimsSearchComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
