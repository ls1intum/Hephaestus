import { Component, output } from '@angular/core';
import { LucideAngularModule, Plus, BotMessageSquare } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-first-session-card',
  standalone: true,
  templateUrl: './first-session-card.component.html',
  imports: [LucideAngularModule, HlmButtonModule]
})
export class FirstSessionCardComponent {
  protected Plus = Plus;
  protected BotMessageSquare = BotMessageSquare;

  createNewSession = output<void>();
}
