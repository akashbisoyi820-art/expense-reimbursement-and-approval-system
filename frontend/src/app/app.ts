import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AssistantComponent } from './components/assistant/assistant.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AssistantComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');
}
