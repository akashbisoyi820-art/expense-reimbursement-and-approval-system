import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ClaimDetailsComponent } from '../../components/claim-details/claim-details.component';
import { ClaimsSearchComponent } from '../../components/claims-search/claims-search.component';
import { AuthService } from '../../services/auth.service';
import { AuditEntry, Expense, ExpenseService } from '../../services/expense.service';

interface StatCard {
  key: string;
  label: string;
  icon: string;
  value: number;
  money: boolean;
  hint: string;
  percent: number | null;
  attention: boolean;
  target: string;
}

@Component({
  selector: 'app-approvals',
  imports: [FormsModule, DecimalPipe, DatePipe, ClaimDetailsComponent, ClaimsSearchComponent],
  templateUrl: './approvals.component.html',
  styleUrl: './approvals.component.css'
})
export class ApprovalsComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private expenseService = inject(ExpenseService);
  private router = inject(Router);

  name = localStorage.getItem('name');
  role = localStorage.getItem('role');
  isAdmin = this.role === 'ADMIN';

  currency = '₹';
  placeholders = this.isAdmin ? [1, 2, 3, 4, 5] : [1, 2, 3, 4];

  pending = signal<Expense[]>([]);
  approved = signal<Expense[]>([]);
  activity = signal<AuditEntry[]>([]);
  loaded = signal(false);
  message = signal('');
  error = signal('');

  rejectingId = signal<number | null>(null);
  rejectReason = '';

  refreshKey = signal(0);
  selectedClaim = signal<Expense | null>(null);

  shown = signal<Record<string, number>>({
    PENDING: 0,
    AMOUNT: 0,
    RECEIPTS: 0,
    EMPLOYEES: 0,
    READY: 0
  });
  private frameId = 0;

  stats = computed<StatCard[]>(() => {
    const pending = this.pending();
    const withReceipt = pending.filter((e) => e.hasReceipt).length;
    const pendingAmount = pending.reduce((sum, e) => sum + Number(e.amount), 0);
    const employees = new Set(pending.map((e) => e.employeeEmail)).size;

    const cards: StatCard[] = [
      {
        key: 'PENDING',
        label: 'Pending Claims',
        icon: '',
        value: pending.length,
        money: false,
        hint: pending.length === 1 ? 'claim awaiting decision' : 'claims awaiting decision',
        percent: null,
        attention: pending.length > 0,
        target: 'pending-card'
      },
      {
        key: 'AMOUNT',
        label: 'Pending Amount',
        icon: this.currency,
        value: pendingAmount,
        money: true,
        hint: 'waiting for a decision',
        percent: null,
        attention: false,
        target: 'pending-card'
      },
      {
        key: 'RECEIPTS',
        label: 'With Receipt',
        icon: '✓',
        value: withReceipt,
        money: false,
        hint: `of ${pending.length} pending`,
        percent: pending.length > 0 ? Math.round((withReceipt / pending.length) * 100) : 0,
        attention: false,
        target: 'pending-card'
      },
      {
        key: 'EMPLOYEES',
        label: 'Employees Waiting',
        icon: 'E',
        value: employees,
        money: false,
        hint: employees === 1 ? 'employee' : 'employees',
        percent: null,
        attention: false,
        target: 'pending-card'
      }
    ];

    if (this.isAdmin) {
      const approved = this.approved();
      const readyAmount = approved.reduce((sum, e) => sum + Number(e.amount), 0);
      cards.push({
        key: 'READY',
        label: 'Ready to Reimburse',
        icon: this.currency,
        value: readyAmount,
        money: true,
        hint: `${approved.length} ${approved.length === 1 ? 'claim' : 'claims'} approved`,
        percent: null,
        attention: approved.length > 0,
        target: 'approved-card'
      });
    }

    return cards;
  });

  ngOnInit() {
    this.loadAll();
  }

  ngOnDestroy() {
    cancelAnimationFrame(this.frameId);
  }

  openDetails(expense: Expense) {
    this.selectedClaim.set(expense);
  }

  closeDetails() {
    this.selectedClaim.set(null);
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

  go(id: string) {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  activityLabel(a: AuditEntry): string {
    switch (a.action) {
      case 'SUBMITTED':
        return 'submitted';
      case 'RECEIPT_UPLOADED':
        return 'uploaded a receipt for';
      case 'APPROVED':
        return 'approved';
      case 'REJECTED':
        return 'rejected';
      case 'REIMBURSED':
        return 'reimbursed';
      default:
        return a.action.toLowerCase();
    }
  }

  loadAll() {
    this.expenseService.getPendingExpenses().subscribe({
      next: (data) => {
        this.pending.set(data);
        this.loaded.set(true);
        this.refreshStats();
      },
      error: () => {
        this.loaded.set(true);
        this.error.set('Failed to load pending expenses');
      }
    });

    if (this.isAdmin) {
      this.expenseService.getApprovedExpenses().subscribe({
        next: (data) => {
          this.approved.set(data);
          this.refreshStats();
        },
        error: () => this.error.set('Failed to load approved expenses')
      });

      this.expenseService.getRecentActivity().subscribe({
        next: (data) => this.activity.set(data),
        error: () => this.error.set('Failed to load recent activity')
      });
    }
  }

  // Reloads the tables, cards and the All Claims search after an action
  private afterAction() {
    this.loadAll();
    this.refreshKey.update((n) => n + 1);
  }

  private refreshStats() {
    const targets: Record<string, number> = {};
    for (const s of this.stats()) {
      targets[s.key] = s.value;
    }
    this.animateTo(targets);
  }

  private animateTo(targets: Record<string, number>) {
    cancelAnimationFrame(this.frameId);

    const from = { ...this.shown() };
    const start = performance.now();
    const duration = 900;

    const step = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);

      const next: Record<string, number> = { ...this.shown() };
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

  viewReceipt(id: number) {
    this.error.set('');
    this.expenseService.viewReceipt(id).subscribe({
      error: () => this.error.set('Failed to open receipt')
    });
  }

  approve(id: number) {
    this.message.set('');
    this.error.set('');
    this.expenseService.approve(id).subscribe({
      next: () => {
        this.message.set(`Expense #${id} approved`);
        this.afterAction();
      },
      error: () => this.error.set('Failed to approve expense')
    });
  }

  startReject(id: number) {
    this.message.set('');
    this.error.set('');
    this.rejectReason = '';
    this.rejectingId.set(id);
  }

  cancelReject() {
    this.rejectingId.set(null);
    this.rejectReason = '';
  }

  confirmReject() {
    const id = this.rejectingId();
    if (id === null) {
      return;
    }
    if (!this.rejectReason.trim()) {
      this.error.set('Please enter a reason for rejection');
      return;
    }

    this.message.set('');
    this.error.set('');
    this.expenseService.reject(id, this.rejectReason.trim()).subscribe({
      next: () => {
        this.message.set(`Expense #${id} rejected`);
        this.cancelReject();
        this.afterAction();
      },
      error: () => this.error.set('Failed to reject expense')
    });
  }

  reimburse(id: number) {
    this.message.set('');
    this.error.set('');
    this.expenseService.reimburse(id).subscribe({
      next: () => {
        this.message.set(`Expense #${id} reimbursed`);
        this.afterAction();
      },
      error: () => this.error.set('Failed to reimburse expense')
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
