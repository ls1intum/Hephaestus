import { Component, inject, Input, OnInit, AfterViewChecked, ElementRef, ViewChild} from '@angular/core';
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

export class MessagesComponent implements OnInit, AfterViewChecked {
  protected Hammer = Hammer;

  securityStore = inject(SecurityStore);
  user = this.securityStore.loadedUser;

  @ViewChild('chatMessagesContainer') private chatMessagesContainer!: ElementRef;
  @Input() messages: { sender: Message.SenderEnum; content: string; timestamp: string }[] = [];

    ngOnInit() { 
        this.scrollToBottom();
    }

    ngAfterViewChecked() {        
        this.scrollToBottom();        
    } 

    private scrollToBottom(): void {
      try {
        this.chatMessagesContainer.nativeElement.scrollTop = this.chatMessagesContainer.nativeElement.scrollHeight;
      } catch (err) {
        console.error('Error scrolling to bottom', err);
      }
    }
}
