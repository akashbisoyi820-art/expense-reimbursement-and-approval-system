import { DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { Expense, ExpenseService } from '../../services/expense.service';

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
  imports: [FormsModule, DecimalPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {
  name = localStorage.getItem('name');
  role = localStorage.getItem('role');

  currency = '₹';
  categories = ['Travel', 'Food', 'Accommodation', 'Office Supplies', 'Other'];
  statuses = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'REIMBURSED'];
  placeholders = [1, 2, 3, 4, 5];

  private readonly maxFileSize = 5 * 1024 * 1024;
  private readonly allowedTypes = ['application/pdf', 'image/png', 'image/jpeg'];

  category = 'Travel';
  amount: number | null = null;
  expenseDate = '';
  description = '';
  selectedFile: File | null = null;

  expenses = signal<Expense[]>([]);
  loaded = signal(false);
  selectedStatus = signal('ALL');
  message = signal('');
  error = signal('');

  shown = signal<Record<string, number>>({
    TOTAL: 0,
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
    REIMBURSED: 0
  });
  private frameId = 0;

  filteredExpenses = computed(() => {
    const status = this.selectedStatus();
    return status === 'ALL'
      ? this.expenses()
      : this.expenses().filter((e) => e.status === status);
  });

  stats = computed<StatCard[]>(() => {
    const list = this.expenses();
    const total = list.reduce((sum, e) => sum + Number(e.amount), 0);

    const make = (key: string, label: string, icon: string, items: Expense[]): StatCard => {
      const amount = items.reduce((sum, e) => sum + Number(e.amount), 0);
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
  }

  ngOnDestroy() {
    cancelAnimationFrame(this.frameId);
  }

  countFor(status: string): number {
    return status === 'ALL'
      ? this.expenses().length
      : this.expenses().filter((e) => e.status === status).length;
  }

  setFilter(status: string) {
    this.selectedStatus.set(status);
  }

  filterFor(statKey: string): string {
    return statKey === 'TOTAL' ? 'ALL' : statKey;
  }

  pickStat(statKey: string, target: HTMLElement) {
    this.setFilter(this.filterFor(statKey));
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

    this.expenseService
      .createExpense({
        category: this.category,
        amount: this.amount,
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

          if (!file) {
            this.message.set('Expense submitted successfully');
            this.loadExpenses();
            return;
          }

          this.expenseService.uploadReceipt(created.id, file).subscribe({
            next: () => {
              this.message.set('Expense submitted with receipt');
              this.loadExpenses();
            },
            error: () => {
              this.error.set(
                'Expense submitted, but the receipt upload failed. You can upload it again from the table below.'
              );
              this.loadExpenses();
            }
          });
        },
        error: () => this.error.set('Failed to submit expense')
      });
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
        this.loadExpenses();
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
