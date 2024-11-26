import { Component, inject, input, OnInit, AfterViewChecked, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message } from '@app/core/modules/openapi';

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, HlmAvatarModule]
})
export class MessagesComponent implements OnInit, AfterViewChecked {
  protected Hammer = Hammer;

  securityStore = inject(SecurityStore);
  user = this.securityStore.loadedUser;
  signedIn = this.securityStore.signedIn;

  messageHistory = input<Message[]>([]);

  @ViewChild('chatMessagesContainer') private chatMessagesContainer!: ElementRef;

  ngOnInit() {
    this.scrollToBottom();
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    try {
      if (this.chatMessagesContainer) {
        this.chatMessagesContainer.nativeElement.scrollTop = this.chatMessagesContainer.nativeElement.scrollHeight;
      }
    } catch (err) {
      return;
    }
  }
}
