import { Component, input } from '@angular/core';
import { HlmSeparatorDirective } from '@spartan-ng/ui-separator-helm';
import { BrnSeparatorComponent } from '@spartan-ng/brain/separator';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';

@Component({
  selector: 'app-chat-summary',
  imports: [BrnSeparatorComponent, HlmSeparatorDirective, HlmCardDirective],
  templateUrl: './chat-summary.component.html'
})
export class ChatSummaryComponent {
  status = input<string[]>([]);
  impediments = input<string[]>([]);
  promises = input<string[]>([]);

  sections = [
    { title: 'Status', data: this.status, isLast: false },
    { title: 'Impediments', data: this.impediments, isLast: false },
    { title: 'Promises', data: this.promises, isLast: true }
  ];
}
