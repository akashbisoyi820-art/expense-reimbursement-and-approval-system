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
import { AuditEntry, ClaimComment, Expense, ExpenseService } from '../../services/expense.service';

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
  loadingHistory = signal(true);
  loadingComments = signal(true);
  error = signal('');
  sending = signal(false);
  newMessage = '';

  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  constructor(private expenseService: ExpenseService) {
    effect(() => {
      const id = this.expense().id;
      untracked(() => this.loadAll(id));
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

  private loadAll(id: number) {
    this.error.set('');
    this.loadingHistory.set(true);
    this.loadingComments.set(true);

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
