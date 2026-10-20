import { Component, ElementRef, HostListener, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { AssistantService } from '../../services/assistant.service';

interface ChatMessage {
  from: 'user' | 'bot';
  text: string;
}

@Component({
  selector: 'app-assistant',
  imports: [FormsModule],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.css'
})
export class AssistantComponent {
  visible = signal(false);
  open = signal(false);
  messages = signal<ChatMessage[]>([]);
  suggestions = signal<string[]>([]);
  typing = signal(false);
  draft = '';

  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  private chatInput = viewChild<ElementRef<HTMLInputElement>>('chatInput');

  constructor(
    private router: Router,
    private assistant: AssistantService
  ) {
    this.refresh();
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => this.refresh());
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.open()) {
      this.open.set(false);
    }
  }

  // The assistant is only shown to logged-in users, and not on the login and register pages
  private refresh() {
    const url = this.router.url;
    const loggedIn = !!localStorage.getItem('token');
    const show = loggedIn && !url.startsWith('/login') && !url.startsWith('/register');

    if (!show && this.visible()) {
      // Logged out: forget the conversation
      this.open.set(false);
      this.messages.set([]);
      this.suggestions.set([]);
      this.typing.set(false);
    }
    this.visible.set(show);
  }

  toggle() {
    this.open.update((value) => !value);
    if (this.open()) {
      if (this.messages().length === 0) {
        this.request('help', false);
      }
      setTimeout(() => this.chatInput()?.nativeElement.focus(), 300);
    }
  }

  clear() {
    this.messages.set([]);
    this.suggestions.set([]);
    this.request('help', false);
  }

  submit() {
    this.ask(this.draft);
  }

  ask(text: string) {
    const message = text.trim();
    if (!message || this.typing()) {
      return;
    }
    this.request(message, true);
  }

  private request(message: string, showUser: boolean) {
    if (showUser) {
      this.messages.update((list) => [...list, { from: 'user', text: message }]);
    }
    this.draft = '';
    this.suggestions.set([]);
    this.typing.set(true);
    this.scrollSoon();

    this.assistant.ask(message).subscribe({
      next: (reply) => {
        // A short pause makes the reply feel like a conversation
        setTimeout(() => {
          this.messages.update((list) => [...list, { from: 'bot', text: reply.answer }]);
          this.suggestions.set(reply.suggestions ?? []);
          this.typing.set(false);
          this.scrollSoon();
        }, 400);
      },
      error: () => {
        this.messages.update((list) => [
          ...list,
          {
            from: 'bot',
            text: 'Sorry, I could not reach the server. Please check that it is running and try again.'
          }
        ]);
        this.typing.set(false);
        this.scrollSoon();
      }
    });
  }

  private scrollSoon() {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    }, 40);
  }
}
