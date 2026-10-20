import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AssistantReply {
  answer: string;
  suggestions: string[];
}

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private url = 'http://localhost:8080/api/assistant/ask';

  constructor(private http: HttpClient) {}

  ask(message: string): Observable<AssistantReply> {
    return this.http.post<AssistantReply>(this.url, { message });
  }
}
