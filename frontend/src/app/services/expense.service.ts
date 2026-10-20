import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface Expense {
  id: number;
  category: string;
  amount: number;
  currency: string;
  amountInBase: number;
  exchangeRate: number;
  rateDate: string | null;
  expenseDate: string;
  description: string;
  status: string;
  employeeName: string;
  employeeEmail: string;
  employeeDepartment: string | null;
  rejectionReason: string | null;
  hasReceipt: boolean;
  policyFlags: string[];
  receiptRequired: boolean;
}

export interface ExpenseRequest {
  category: string;
  amount: number;
  currency: string;
  expenseDate: string;
  description: string;
}

export interface PaymentRequest {
  paymentMethod: string;
  referenceNo?: string;
  paidOn?: string;
  note?: string;
}

export interface PaymentRecord {
  id: number;
  expenseId: number;
  paymentMethod: string;
  referenceNo: string | null;
  paidOn: string;
  amount: number;
  note: string | null;
  paidByName: string;
  createdAt: string;
}

export interface PolicyRule {
  id: number;
  category: string;
  maxPerClaim: number | null;
  monthlyLimit: number | null;
  receiptRequiredAbove: number | null;
  updatedAt: string | null;
  updatedByName: string | null;
}

export interface PolicyRuleInput {
  category: string;
  maxPerClaim: number | null;
  monthlyLimit: number | null;
  receiptRequiredAbove: number | null;
}

export interface PolicyChange {
  id: number;
  summary: string;
  changedByName: string;
  createdAt: string;
}

export interface ChartPoint {
  label: string;
  total: number;
  count: number;
}

export interface Analytics {
  currency: string;
  grandTotal: number;
  claimCount: number;
  byCategory: ChartPoint[];
  byMonth: ChartPoint[];
  byDepartment: ChartPoint[];
  byStatus: ChartPoint[];
}

export interface CurrencyInfo {
  code: string;
  name: string;
}

export interface RateQuote {
  currency: string;
  baseCurrency: string;
  rate: number;
  rateDate: string;
}

export interface AuditEntry {
  id: number;
  expenseId: number;
  action: string;
  actorName: string;
  actorRole: string;
  fromStatus: string | null;
  toStatus: string | null;
  details: string | null;
  createdAt: string;
}

export interface ClaimComment {
  id: number;
  authorName: string;
  authorRole: string;
  message: string;
  createdAt: string;
  mine: boolean;
}

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ExpenseFilters {
  keyword?: string;
  status?: string;
  category?: string;
  employee?: string;
  department?: string;
  from?: string;
  to?: string;
  minAmount?: number | null;
  maxAmount?: number | null;
}

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private apiRoot = 'http://localhost:8080/api';
  private baseUrl = `${this.apiRoot}/expenses`;

  constructor(private http: HttpClient) {}

  createExpense(request: ExpenseRequest): Observable<Expense> {
    return this.http.post<Expense>(this.baseUrl, request);
  }

  getMyExpenses(): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.baseUrl}/my`);
  }

  getPendingExpenses(): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.baseUrl}/pending`);
  }

  getApprovedExpenses(): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.baseUrl}/approved`);
  }

  searchExpenses(
    filters: ExpenseFilters,
    page: number,
    size: number,
    sortBy: string,
    direction: string
  ): Observable<PageResult<Expense>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('direction', direction);

    const entries: [string, string | number | null | undefined][] = [
      ['keyword', filters.keyword?.trim()],
      ['status', filters.status && filters.status !== 'ALL' ? filters.status : undefined],
      ['category', filters.category && filters.category !== 'ALL' ? filters.category : undefined],
      ['employee', filters.employee?.trim()],
      ['department', filters.department?.trim()],
      ['from', filters.from],
      ['to', filters.to],
      ['minAmount', filters.minAmount],
      ['maxAmount', filters.maxAmount]
    ];

    for (const [key, value] of entries) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }

    return this.http.get<PageResult<Expense>>(`${this.baseUrl}/search`, { params });
  }

  approve(id: number): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/${id}/reject`, { reason });
  }

  reimburse(id: number, payment: PaymentRequest): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/${id}/reimburse`, payment);
  }

  // The server answers 204 (no body) when no payment is recorded, which arrives as null
  getPayment(id: number): Observable<PaymentRecord | null> {
    return this.http.get<PaymentRecord | null>(`${this.baseUrl}/${id}/payment`);
  }

  uploadReceipt(id: number, file: File): Observable<Expense> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Expense>(`${this.baseUrl}/${id}/receipt`, formData);
  }

  viewReceipt(id: number): Observable<void> {
    return this.http
      .get(`${this.baseUrl}/${id}/receipt`, { responseType: 'blob' })
      .pipe(
        map((blob) => {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank');
          setTimeout(() => URL.revokeObjectURL(url), 60000);
        })
      );
  }

  getHistory(id: number): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.baseUrl}/${id}/history`);
  }

  getComments(id: number): Observable<ClaimComment[]> {
    return this.http.get<ClaimComment[]>(`${this.baseUrl}/${id}/comments`);
  }

  addComment(id: number, message: string): Observable<ClaimComment> {
    return this.http.post<ClaimComment>(`${this.baseUrl}/${id}/comments`, { message });
  }

  getRecentActivity(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.apiRoot}/audit/recent`);
  }

  getCurrencies(): Observable<CurrencyInfo[]> {
    return this.http.get<CurrencyInfo[]>(`${this.apiRoot}/currencies`);
  }

  getRate(currency: string): Observable<RateQuote> {
    const params = new HttpParams().set('currency', currency);
    return this.http.get<RateQuote>(`${this.apiRoot}/currencies/rate`, { params });
  }

  getAnalytics(status?: string, from?: string, to?: string): Observable<Analytics> {
    let params = new HttpParams();
    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<Analytics>(`${this.apiRoot}/analytics/summary`, { params });
  }

  getPolicy(): Observable<PolicyRule[]> {
    return this.http.get<PolicyRule[]>(`${this.apiRoot}/policy`);
  }

  updatePolicy(rules: PolicyRuleInput[]): Observable<PolicyRule[]> {
    return this.http.put<PolicyRule[]>(`${this.apiRoot}/policy`, { rules });
  }

  getPolicyHistory(): Observable<PolicyChange[]> {
    return this.http.get<PolicyChange[]>(`${this.apiRoot}/policy/history`);
  }
}
