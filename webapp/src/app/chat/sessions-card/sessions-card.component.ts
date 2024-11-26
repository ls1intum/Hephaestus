import { Component, input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Plus } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { Session } from '@app/core/modules/openapi';

@Component({
  standalone: true,
  selector: 'app-sessions-card',
  templateUrl: './sessions-card.component.html',
  imports: [CommonModule, LucideAngularModule, HlmButtonModule]
})
export class SessionsCardComponent {
  protected Plus = Plus;

  sessions = input<Session[]>();
  activeSessionId = input<number|null>();

  @Output() sessionSelected = new EventEmitter<number>();
  @Output() createSession = new EventEmitter<void>();

  handleSelectSession(sessionId: number): void {
    if (this.activeSessionId() && this.activeSessionId() !== sessionId) {
      this.sessionSelected.emit(sessionId);
    }
  }

  handleCreateSession(): void {
    this.createSession.emit();
  }
}
