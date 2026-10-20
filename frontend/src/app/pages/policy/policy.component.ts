import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ExpenseService, PolicyChange, PolicyRule } from '../../services/expense.service';

interface RuleValues {
  maxPerClaim: number | null;
  monthlyLimit: number | null;
  receiptRequiredAbove: number | null;
}

// One editable row: the current inputs, plus the values saved on the server
interface RuleRow extends RuleValues {
  category: string;
  saved: RuleValues;
  updatedAt: string | null;
  updatedByName: string | null;
}

@Component({
  selector: 'app-policy',
  imports: [FormsModule, DatePipe],
  templateUrl: './policy.component.html',
  styleUrl: './policy.component.css'
})
export class PolicyComponent implements OnInit {
  name = localStorage.getItem('name');
  role = localStorage.getItem('role');

  rows = signal<RuleRow[]>([]);
  history = signal<PolicyChange[]>([]);
  loading = signal(true);
  saving = signal(false);
  message = signal('');
  error = signal('');

  constructor(
    private authService: AuthService,
    private expenseService: ExpenseService,
    private router: Router
  ) {}

  ngOnInit() {
    this.load();
    this.loadHistory();
  }

  private toRow(rule: PolicyRule): RuleRow {
    const values: RuleValues = {
      maxPerClaim: rule.maxPerClaim,
      monthlyLimit: rule.monthlyLimit,
      receiptRequiredAbove: rule.receiptRequiredAbove
    };
    return {
      category: rule.category,
      ...values,
      saved: { ...values },
      updatedAt: rule.updatedAt,
      updatedByName: rule.updatedByName
    };
  }

  private load() {
    this.loading.set(true);
    this.expenseService.getPolicy().subscribe({
      next: (rules) => {
        this.rows.set(rules.map((r) => this.toRow(r)));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Could not load the policy');
      }
    });
  }

  private loadHistory() {
    this.expenseService.getPolicyHistory().subscribe({
      next: (list) => this.history.set(list),
      error: () => this.history.set([])
    });
  }

  isChanged(row: RuleRow): boolean {
    return (
      row.maxPerClaim !== row.saved.maxPerClaim ||
      row.monthlyLimit !== row.saved.monthlyLimit ||
      row.receiptRequiredAbove !== row.saved.receiptRequiredAbove
    );
  }

  changedCount(): number {
    return this.rows().filter((r) => this.isChanged(r)).length;
  }

  rowError(row: RuleRow): string {
    const values = [row.maxPerClaim, row.monthlyLimit, row.receiptRequiredAbove];
    if (values.some((v) => v !== null && v < 0)) {
      return 'Amounts cannot be negative';
    }
    if (
      row.maxPerClaim !== null &&
      row.monthlyLimit !== null &&
      row.monthlyLimit < row.maxPerClaim
    ) {
      return 'The monthly limit cannot be below the per-claim limit';
    }
    return '';
  }

  save() {
    this.message.set('');
    this.error.set('');

    const changed = this.rows().filter((r) => this.isChanged(r));
    if (changed.length === 0) {
      this.error.set('There are no changes to save');
      return;
    }
    if (changed.some((r) => this.rowError(r))) {
      this.error.set('Please fix the highlighted rows before saving');
      return;
    }

    this.saving.set(true);
    this.expenseService
      .updatePolicy(
        changed.map((r) => ({
          category: r.category,
          maxPerClaim: r.maxPerClaim,
          monthlyLimit: r.monthlyLimit,
          receiptRequiredAbove: r.receiptRequiredAbove
        }))
      )
      .subscribe({
        next: (rules) => {
          this.rows.set(rules.map((r) => this.toRow(r)));
          this.message.set(
            `Policy updated for ${changed.length} ${changed.length === 1 ? 'category' : 'categories'}`
          );
          this.saving.set(false);
          this.loadHistory();
        },
        error: () => {
          this.saving.set(false);
          this.error.set('Could not save the policy. Please check the values and try again.');
        }
      });
  }

  discard() {
    this.message.set('');
    this.error.set('');
    this.rows.update((list) =>
      list.map((r) => ({
        ...r,
        maxPerClaim: r.saved.maxPerClaim,
        monthlyLimit: r.saved.monthlyLimit,
        receiptRequiredAbove: r.saved.receiptRequiredAbove
      }))
    );
  }

  back() {
    this.router.navigate(['/approvals']);
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
