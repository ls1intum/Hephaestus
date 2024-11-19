import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { SecurityStore } from '@app/core/security/security-store.service';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';

import { Send, Hammer, LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  standalone: true,
  imports: [ 
    FormsModule, 
    CommonModule, 
    LucideAngularModule,
    HlmAvatarModule],
})
export class ChatComponent {
  protected Send = Send;
  protected Hammer = Hammer; 

  securityStore = inject(SecurityStore);
  user = this.securityStore.loadedUser;
}