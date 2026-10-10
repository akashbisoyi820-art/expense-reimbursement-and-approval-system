import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Expense,
  ExpenseFilters,
  ExpenseService,
  PageResult
} from '../../services/expense.service';

@Component({
  selector: 'app-claims-search',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './claims-search.component.html',
  styleUrl: './claims-search.component.css'
})
export class ClaimsSearchComponent {
  heading = input('Claims');
  showEmployee = input(false);
  allowUpload = input(false);
  refresh = input(0);

  details = output<Expense>();
  receipt = output<number>();
  upload = output<{ id: number; event: Event }>();
  statusChange = output<string>();

  statuses = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'REIMBURSED'];
  categories = ['Travel', 'Food', 'Accommodation', 'Office Supplies', 'Other'];
  pageSizes = [5, 10, 20, 50];
  currency = '₹';

  // Filter fields (applied only when Search is pressed)
  keyword = '';
  category = 'ALL';
  employee = '';
  from = '';
  to = '';
  minAmount: number | null = null;
  maxAmount: number | null = null;
  status = signal('ALL');

  showAdvanced = signal(false);

  result = signal<PageResult<Expense> | null>(null);
  loading = signal(false);
  error = signal('');
  page = signal(0);
  size = signal(10);
  sortBy = signal('expenseDate');
  direction = signal<'asc' | 'desc'>('desc');

  private applied = signal<ExpenseFilters>({});
  private requestId = 0;

  rows = computed(() => this.result()?.content ?? []);
  total = computed(() => this.result()?.totalElements ?? 0);
  totalPages = computed(() => this.result()?.totalPages ?? 0);
  firstItem = computed(() => (this.total() === 0 ? 0 : this.page() * this.size() + 1));
  lastItem = computed(() => Math.min((this.page() + 1) * this.size(), this.total()));

  pageButtons = computed(() => {
    const total = this.totalPages();
    const start = Math.max(0, Math.min(this.page() - 2, total - 5));
    const end = Math.min(total, start + 5);
    const pages: number[] = [];
    for (let i = start; i < end; i++) {
      pages.push(i);
    }
    return pages;
  });

  activeAdvanced = computed(() => {
    const a = this.applied();
    let count = 0;
    if (a.category && a.category !== 'ALL') count++;
    if (a.employee && a.employee.trim()) count++;
    if (a.from) count++;
    if (a.to) count++;
    if (a.minAmount !== null && a.minAmount !== undefined) count++;
    if (a.maxAmount !== null && a.maxAmount !== undefined) count++;
    return count;
  });

  constructor(private expenseService: ExpenseService) {
    // Runs once on start, and again whenever the parent bumps `refresh`
    effect(() => {
      this.refresh();
      untracked(() => this.run());
    });
  }

  search() {
    this.error.set('');

    if (this.from && this.to && this.from > this.to) {
      this.error.set('"From" date must not be after "To" date');
      return;
    }
    if (this.minAmount !== null && this.maxAmount !== null && this.minAmount > this.maxAmount) {
      this.error.set('Minimum amount must not be greater than maximum amount');
      return;
    }

    this.applied.set({
      keyword: this.keyword,
      status: this.status(),
      category: this.category,
      employee: this.employee,
      from: this.from,
      to: this.to,
      minAmount: this.minAmount,
      maxAmount: this.maxAmount
    });
    this.page.set(0);
    this.run();
  }

  reset() {
    this.keyword = '';
    this.category = 'ALL';
    this.employee = '';
    this.from = '';
    this.to = '';
    this.minAmount = null;
    this.maxAmount = null;
    this.status.set('ALL');
    this.statusChange.emit('ALL');
    this.sortBy.set('expenseDate');
    this.direction.set('desc');
    this.search();
  }

  // Also called by the parent page when a summary card is clicked
  setStatus(status: string) {
    this.status.set(status);
    this.statusChange.emit(status);
    this.search();
  }

  goTo(page: number) {
    if (page < 0 || page >= this.totalPages() || page === this.page()) {
      return;
    }
    this.page.set(page);
    this.run();
  }

  changeSize(value: number) {
    this.size.set(Number(value));
    this.page.set(0);
    this.run();
  }

  sort(field: string) {
    if (this.sortBy() === field) {
      this.direction.set(this.direction() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortBy.set(field);
      this.direction.set(
        field === 'expenseDate' || field === 'amount' || field === 'id' ? 'desc' : 'asc'
      );
    }
    this.page.set(0);
    this.run();
  }

  arrow(field: string): string {
    if (this.sortBy() !== field) {
      return '';
    }
    return this.direction() === 'asc' ? '▲' : '▼';
  }

  private run() {
    const id = ++this.requestId;
    this.loading.set(true);

    this.expenseService
      .searchExpenses(this.applied(), this.page(), this.size(), this.sortBy(), this.direction())
      .subscribe({
        next: (res) => {
          if (id !== this.requestId) {
            return;
          }
          // The current page became empty (for example the last claim moved away)
          if (res.content.length === 0 && res.page > 0 && res.totalPages > 0) {
            this.page.set(res.totalPages - 1);
            this.run();
            return;
          }
          this.result.set(res);
          this.loading.set(false);
        },
        error: () => {
          if (id !== this.requestId) {
            return;
          }
          this.loading.set(false);
          this.error.set('Could not load claims. Please try again.');
        }
      });
  }
}
