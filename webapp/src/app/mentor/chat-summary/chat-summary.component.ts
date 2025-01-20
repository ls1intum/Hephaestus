import { Component, input } from '@angular/core';
import { HlmUlDirective } from '@spartan-ng/ui-typography-helm';
import { HlmSeparatorDirective } from '@spartan-ng/ui-separator-helm';
import { BrnSeparatorComponent } from '@spartan-ng/ui-separator-brain';


@Component({
  selector: 'app-chat-summary',
  standalone: true,
  imports: [BrnSeparatorComponent, HlmSeparatorDirective, HlmUlDirective],
  templateUrl: './chat-summary.component.html'
})
export class ChatSummaryComponent {
  status = input<string[]>([]);
  impediments = input<string[]>([]);
  promises = input<string[]>([]);
}
