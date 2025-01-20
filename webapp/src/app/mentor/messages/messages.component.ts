import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, BotMessageSquare } from 'lucide-angular';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { Message } from '@app/core/modules/openapi';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { ChatSummaryComponent } from '../chat-summary/chat-summary.component';

export interface Summary extends Message {
  status: string[];
  impediments: string[];
  promises: string[];
  text: string;
}

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, HlmAvatarModule, HlmSkeletonComponent, ChatSummaryComponent]
})
export class MessagesComponent {
  protected BotMessageSquare = BotMessageSquare;
  protected Message = Message;

  securityStore = inject(SecurityStore);
  messages = input<(Message | Summary)[]>([]);
  isLoading = input<boolean>(false);


  getSummary(message: Message): Summary | null {
      const content = message.content;
      if (!content.includes("SUMMARY")) {
        return null;
      }
  
      const result: Summary = {
        ...message,
        content: '',
        status: [],
        impediments: [],
        promises: [],
        text: ''
      };
  
      const sections = content.split(/(?=STATUS|IMPEDIMENTS|PROMISES|TEXT)/).slice(1);
      
      sections.forEach(section => {
        const lines = section.trim().split('\n');
        const sectionType = lines[0].trim();
        const items = lines.slice(1).filter(line => line.trim());
  
        switch (sectionType) {
          case 'STATUS':
            result.status = items;
            break;
          case 'IMPEDIMENTS':
            result.impediments = items;
            break;
          case 'PROMISES':
            result.promises = items;
            break;
          case 'TEXT':
            result.text = items.join('\n');
            break;
        }
      });
  
      return result;
    }


  isSummary(item: Message | Summary): item is Summary {
    return 'status' in item && 'impediments' in item && 'promises' in item;
  }
}
