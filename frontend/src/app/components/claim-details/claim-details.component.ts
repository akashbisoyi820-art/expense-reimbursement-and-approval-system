import { DatePipe, DecimalPipe } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  effect,
  input,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AuditEntry,
  ClaimComment,
  Expense,
  ExpenseService,
  PaymentRecord
} from '../../services/expense.service';

type Tab = 'timeline' | 'comments';

@Component({
  selector: 'app-claim-details',
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './claim-details.component.html',
  styleUrl: './claim-details.component.css'
})
export class ClaimDetailsComponent {
  expense = input.required<Expense>();
  closed = output<void>();

  currency = '₹';
  tab = signal<Tab>('timeline');

  history = signal<AuditEntry[]>([]);
  comments = signal<ClaimComment[]>([]);
  payment = signal<PaymentRecord | null>(null);
  loadingHistory = signal(true);
  loadingComments = signal(true);
  error = signal('');
  sending = signal(false);
  newMessage = '';

  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  constructor(private expenseService: ExpenseService) {
    effect(() => {
      const claim = this.expense();
      untracked(() => this.loadAll(claim.id, claim.status));
    });
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.close();
  }

  close() {
    this.closed.emit();
  }

  setTab(next: Tab) {
    this.tab.set(next);
    this.scrollTo(next === 'comments' ? 'bottom' : 'top');
  }

  actionLabel(action: string): string {
    switch (action) {
      case 'SUBMITTED':
        return 'Claim submitted';
      case 'RECEIPT_UPLOADED':
        return 'Receipt uploaded';
      case 'APPROVED':
        return 'Approved';
      case 'REJECTED':
        return 'Rejected';
      case 'REIMBURSED':
        return 'Reimbursed';
      default:
        return action;
    }
  }

  methodLabel(method: string): string {
    return method.charAt(0) + method.slice(1).toLowerCase().replace('_', ' ');
  }

  initial(name: string): string {
    return name ? name.charAt(0).toUpperCase() : '?';
  }

  sendComment() {
    const message = this.newMessage.trim();
    if (!message || this.sending()) {
      return;
    }

    this.sending.set(true);
    this.error.set('');

    this.expenseService.addComment(this.expense().id, message).subscribe({
      next: (comment) => {
        this.comments.update((list) => [...list, comment]);
        this.newMessage = '';
        this.sending.set(false);
        this.scrollTo('bottom');
      },
      error: () => {
        this.sending.set(false);
        this.error.set('Could not send the comment');
      }
    });
  }

  private loadAll(id: number, status: string) {
    this.error.set('');
    this.loadingHistory.set(true);
    this.loadingComments.set(true);
    this.payment.set(null);

    this.expenseService.getHistory(id).subscribe({
      next: (data) => {
        this.history.set(data);
        this.loadingHistory.set(false);
      },
      error: () => {
        this.loadingHistory.set(false);
        this.error.set('Could not load the history');
      }
    });

    this.expenseService.getComments(id).subscribe({
      next: (data) => {
        this.comments.set(data);
        this.loadingComments.set(false);
        if (this.tab() === 'comments') {
          this.scrollTo('bottom');
        }
      },
      error: () => {
        this.loadingComments.set(false);
        this.error.set('Could not load the comments');
      }
    });

    // Only reimbursed claims can have a payment record
    if (status === 'REIMBURSED') {
      this.expenseService.getPayment(id).subscribe({
        next: (record) => this.payment.set(record),
        error: () => {
          /* the payment block is optional, so a failure just hides it */
        }
      });
    }
  }

  private scrollTo(where: 'top' | 'bottom') {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) {
        el.scrollTop = where === 'bottom' ? el.scrollHeight : 0;
      }
    }, 50);
  }
}
