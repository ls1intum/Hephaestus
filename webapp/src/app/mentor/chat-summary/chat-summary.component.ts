import { Component, input } from '@angular/core';
import { HlmUlDirective } from '@spartan-ng/ui-typography-helm';
import { HlmSeparatorDirective } from '@spartan-ng/ui-separator-helm';
import { BrnSeparatorComponent } from '@spartan-ng/brain/separator';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';

@Component({
  selector: 'app-chat-summary',
  imports: [BrnSeparatorComponent, HlmSeparatorDirective, HlmUlDirective, HlmCardDirective],
  templateUrl: './chat-summary.component.html'
})
export class ChatSummaryComponent {
  status = input<string[]>([]);
  impediments = input<string[]>([]);
  promises = input<string[]>([]);
}
