import { DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AnalyticsChartsComponent } from '../../components/analytics-charts/analytics-charts.component';
import { ClaimDetailsComponent } from '../../components/claim-details/claim-details.component';
import { ClaimsSearchComponent } from '../../components/claims-search/claims-search.component';
import { AuthService } from '../../services/auth.service';
import {
  CurrencyInfo,
  Expense,
  ExpenseService,
  PolicyRule,
  RateQuote
} from '../../services/expense.service';

interface StatCard {
  key: string;
  label: string;
  icon: string;
  count: number;
  amount: number;
  percent: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [
    FormsModule,
    DecimalPipe,
    ClaimDetailsComponent,
    ClaimsSearchComponent,
    AnalyticsChartsComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {
  name = localStorage.getItem('name');
  role = localStorage.getItem('role');

  // Symbol of the base currency. All totals are shown in INR.
  currency = '₹';
  categories = ['Travel', 'Food', 'Accommodation', 'Office Supplies', 'Other'];
  placeholders = [1, 2, 3, 4, 5];

  private readonly maxFileSize = 5 * 1024 * 1024;
  private readonly allowedTypes = ['application/pdf', 'image/png', 'image/jpeg'];

  category = 'Travel';
  claimCurrency = 'INR';
  amount: number | null = null;
  expenseDate = '';
  description = '';
  selectedFile: File | null = null;

  currencies = signal<CurrencyInfo[]>([{ code: 'INR', name: 'Indian Rupee' }]);
  policy = signal<PolicyRule[]>([]);
  rateQuote = signal<RateQuote | null>(null);
  rateLoading = signal(false);
  rateError = signal('');
  private rateCache = new Map<string, RateQuote>();

  expenses = signal<Expense[]>([]);
  loaded = signal(false);
  selectedStatus = signal('ALL');
  message = signal('');
  error = signal('');

  search = viewChild(ClaimsSearchComponent);
  refreshKey = signal(0);
  selectedClaim = signal<Expense | null>(null);

  shown = signal<Record<string, number>>({
    TOTAL: 0,
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
    REIMBURSED: 0
  });
  private frameId = 0;

  stats = computed<StatCard[]>(() => {
    const list = this.expenses();
    const total = list.reduce((sum, e) => sum + this.amountOf(e), 0);

    const make = (key: string, label: string, icon: string, items: Expense[]): StatCard => {
      const amount = items.reduce((sum, e) => sum + this.amountOf(e), 0);
      return {
        key,
        label,
        icon,
        count: items.length,
        amount,
        percent: total > 0 ? Math.round((amount / total) * 100) : 0
      };
    };

    return [
      make('TOTAL', 'Total Claimed', 'Σ', list),
      make('PENDING', 'Pending', '', list.filter((e) => e.status === 'PENDING')),
      make('APPROVED', 'Approved', '✓', list.filter((e) => e.status === 'APPROVED')),
      make('REJECTED', 'Rejected', '✕', list.filter((e) => e.status === 'REJECTED')),
      make('REIMBURSED', 'Reimbursed', this.currency, list.filter((e) => e.status === 'REIMBURSED'))
    ];
  });

  constructor(
    private authService: AuthService,
    private expenseService: ExpenseService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadExpenses();
    this.loadCurrencies();
    this.loadPolicy();
  }

  ngOnDestroy() {
    cancelAnimationFrame(this.frameId);
  }

  // Amount in INR (older claims fall back to their own amount)
  private amountOf(e: Expense): number {
    return Number(e.amountInBase ?? e.amount);
  }

  private loadCurrencies() {
    this.expenseService.getCurrencies().subscribe({
      next: (list) => {
        if (list.length > 0) {
          this.currencies.set(list);
        }
      },
      error: () => {
        /* keep INR only */
      }
    });
  }

  private loadPolicy() {
    this.expenseService.getPolicy().subscribe({
      next: (rules) => this.policy.set(rules),
      error: () => {
        /* the form simply shows no policy hints */
      }
    });
  }

  // ---------- Policy hints for the claim form ----------

  private ruleFor(category: string): PolicyRule | null {
    return this.policy().find((r) => r.category.toLowerCase() === category.toLowerCase()) ?? null;
  }

  private money(value: number): string {
    return `${this.currency}${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  }

  // The amount being entered, in INR (null while the exchange rate is not known yet)
  private currentInr(): number | null {
    if (!this.amount) {
      return null;
    }
    if (this.claimCurrency === 'INR') {
      return this.amount;
    }
    const quote = this.rateQuote();
    return quote ? this.amount * quote.rate : null;
  }

  policyLines(): string[] {
    const rule = this.ruleFor(this.category);
    if (!rule) {
      return [];
    }

    const lines: string[] = [];
    if (rule.maxPerClaim !== null) {
      lines.push(`Up to ${this.money(rule.maxPerClaim)} per claim`);
    }
    if (rule.monthlyLimit !== null) {
      lines.push(`${this.money(rule.monthlyLimit)} per month in total`);
    }
    if (rule.receiptRequiredAbove !== null) {
      lines.push(
        Number(rule.receiptRequiredAbove) === 0
          ? 'A receipt is always required'
          : `Receipt required above ${this.money(rule.receiptRequiredAbove)}`
      );
    }
    return lines;
  }

  overLimit(): boolean {
    const rule = this.ruleFor(this.category);
    const inr = this.currentInr();
    return !!rule && rule.maxPerClaim !== null && inr !== null && inr > Number(rule.maxPerClaim);
  }

  needsReceipt(): boolean {
    const rule = this.ruleFor(this.category);
    const inr = this.currentInr();
    return (
      !!rule &&
      rule.receiptRequiredAbove !== null &&
      inr !== null &&
      inr > Number(rule.receiptRequiredAbove)
    );
  }

  // ---------- Currency ----------

  setCurrency(code: string) {
    this.claimCurrency = code;
    this.rateError.set('');

    if (code === 'INR') {
      this.rateQuote.set(null);
      return;
    }

    const cached = this.rateCache.get(code);
    if (cached) {
      this.rateQuote.set(cached);
      return;
    }

    this.rateQuote.set(null);
    this.rateLoading.set(true);

    this.expenseService.getRate(code).subscribe({
      next: (quote) => {
        this.rateCache.set(code, quote);
        if (this.claimCurrency === code) {
          this.rateQuote.set(quote);
        }
        this.rateLoading.set(false);
      },
      error: () => {
        this.rateLoading.set(false);
        if (this.claimCurrency === code) {
          this.rateError.set('Could not fetch the exchange rate. Try again, or choose INR.');
        }
      }
    });
  }

  inrPreview(): number | null {
    const quote = this.rateQuote();
    if (!this.amount || !quote) {
      return null;
    }
    return Math.round(this.amount * quote.rate * 100) / 100;
  }

  // ---------- Page helpers ----------

  openDetails(expense: Expense) {
    this.selectedClaim.set(expense);
  }

  closeDetails() {
    this.selectedClaim.set(null);
  }

  filterFor(statKey: string): string {
    return statKey === 'TOTAL' ? 'ALL' : statKey;
  }

  pickStat(statKey: string, target: HTMLElement) {
    this.search()?.setStatus(this.filterFor(statKey));
    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  tilt(event: MouseEvent) {
    const el = event.currentTarget as HTMLElement;
    const rect = el.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width - 0.5;
    const y = (event.clientY - rect.top) / rect.height - 0.5;
    el.style.setProperty('--ry', `${x * 14}deg`);
    el.style.setProperty('--rx', `${-y * 14}deg`);
  }

  resetTilt(event: MouseEvent) {
    const el = event.currentTarget as HTMLElement;
    el.style.setProperty('--rx', '0deg');
    el.style.setProperty('--ry', '0deg');
  }

  loadExpenses() {
    this.expenseService.getMyExpenses().subscribe({
      next: (data) => {
        this.expenses.set(data);
        this.loaded.set(true);

        const targets: Record<string, number> = {};
        for (const s of this.stats()) {
          targets[s.key] = s.amount;
        }
        this.animateTo(targets);
      },
      error: () => {
        this.loaded.set(true);
        this.error.set('Failed to load expenses');
      }
    });
  }

  // Refreshes the summary cards and the search table together
  private refreshAll() {
    this.loadExpenses();
    this.refreshKey.update((n) => n + 1);
  }

  private animateTo(targets: Record<string, number>) {
    cancelAnimationFrame(this.frameId);

    const from = { ...this.shown() };
    const start = performance.now();
    const duration = 900;

    const step = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);

      const next: Record<string, number> = {};
      for (const key of Object.keys(targets)) {
        const startValue = from[key] ?? 0;
        next[key] = startValue + (targets[key] - startValue) * eased;
      }
      this.shown.set(next);

      if (t < 1) {
        this.frameId = requestAnimationFrame(step);
      }
    };

    this.frameId = requestAnimationFrame(step);
  }

  private validateFile(file: File): string | null {
    if (!this.allowedTypes.includes(file.type)) {
      return 'Only PDF, PNG and JPG files are allowed';
    }
    if (file.size > this.maxFileSize) {
      return 'File is too large (max 5 MB)';
    }
    return null;
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.error.set('');

    if (file) {
      const problem = this.validateFile(file);
      if (problem) {
        this.error.set(problem);
        input.value = '';
        this.selectedFile = null;
        return;
      }
    }
    this.selectedFile = file;
  }

  submitExpense(fileInput: HTMLInputElement) {
    this.message.set('');
    this.error.set('');

    if (!this.amount || !this.expenseDate) {
      this.error.set('Amount and date are required');
      return;
    }

    if (this.needsReceipt() && !this.selectedFile) {
      this.error.set(
        'A receipt is required for this amount. Please attach one before submitting.'
      );
      return;
    }

    this.expenseService
      .createExpense({
        category: this.category,
        amount: this.amount,
        currency: this.claimCurrency,
        expenseDate: this.expenseDate,
        description: this.description
      })
      .subscribe({
        next: (created) => {
          const file = this.selectedFile;

          this.amount = null;
          this.expenseDate = '';
          this.description = '';
          this.selectedFile = null;
          fileInput.value = '';

          const flagged = created.policyFlags.length > 0;
          const note = flagged
            ? ' It is over the policy limit, so your manager will see a warning.'
            : '';

          if (!file) {
            this.message.set('Expense submitted successfully.' + note);
            this.refreshAll();
            return;
          }

          this.expenseService.uploadReceipt(created.id, file).subscribe({
            next: () => {
              this.message.set('Expense submitted with receipt.' + note);
              this.refreshAll();
            },
            error: () => {
              this.error.set(
                'Expense submitted, but the receipt upload failed. You can upload it again from the table below.'
              );
              this.refreshAll();
            }
          });
        },
        error: (err) => {
          this.error.set(
            err.status === 503
              ? 'The exchange rate service is unavailable right now. Try again, or choose INR.'
              : 'Failed to submit expense'
          );
        }
      });
  }

  onUpload(payload: { id: number; event: Event }) {
    this.uploadFor(payload.id, payload.event);
  }

  uploadFor(id: number, event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.message.set('');
    this.error.set('');

    const problem = this.validateFile(file);
    if (problem) {
      this.error.set(problem);
      input.value = '';
      return;
    }

    this.expenseService.uploadReceipt(id, file).subscribe({
      next: () => {
        this.message.set(`Receipt uploaded for expense #${id}`);
        this.refreshAll();
      },
      error: () => {
        this.error.set('Failed to upload receipt');
        input.value = '';
      }
    });
  }

  viewReceipt(id: number) {
    this.error.set('');
    this.expenseService.viewReceipt(id).subscribe({
      error: () => this.error.set('Failed to open receipt')
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
