import { Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message } from '@app/core/modules/openapi';

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, HlmAvatarModule,],
})
export class MessagesComponent {
  protected Hammer = Hammer;

  securityStore = inject(SecurityStore);
  user = this.securityStore.loadedUser;

  @Input() messages: { sender: Message.SenderEnum; content: string; timestamp: string }[] = [];
}
