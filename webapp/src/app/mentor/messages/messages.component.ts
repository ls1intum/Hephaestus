import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, BotMessageSquare } from 'lucide-angular';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message } from '@app/core/modules/openapi';

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, HlmAvatarModule]
})
export class MessagesComponent {
  protected BotMessageSquare = BotMessageSquare;
  protected Message = Message;

  securityStore = inject(SecurityStore);
  messages = input<Message[]>([]);
}
