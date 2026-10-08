import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface Expense {
  id: number;
  category: string;
  amount: number;
  expenseDate: string;
  description: string;
  status: string;
  employeeName: string;
  employeeEmail: string;
  rejectionReason: string | null;
  hasReceipt: boolean;
}

export interface ExpenseRequest {
  category: string;
  amount: number;
  expenseDate: string;
  description: string;
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

  approve(id: number): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/${id}/reject`, { reason });
  }

  reimburse(id: number): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/${id}/reimburse`, {});
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
}
