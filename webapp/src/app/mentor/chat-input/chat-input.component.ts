import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideSend, lucideCircleAlert } from '@ng-icons/lucide';
import { HlmAlertDescriptionDirective, HlmAlertDirective, HlmAlertTitleDirective } from '@spartan-ng/ui-alert-helm';

@Component({
  selector: 'app-chat-input',
  templateUrl: './chat-input.component.html',
  imports: [
    CommonModule,
    HlmButtonModule,
    HlmAlertDescriptionDirective,
    HlmAlertDirective,
    HlmAlertTitleDirective,
    HlmSpinnerComponent,
    FormsModule,
    HlmCardModule,
    HlmInputDirective,
    NgIconComponent
  ],
  providers: [provideIcons({ lucideSend, lucideCircleAlert })]
})
export class ChatInputComponent {
  isClosed = input.required<boolean>();
  isSending = input.required<boolean>();
  message = signal<string>('');
  sendMessage = output<string>();

  onSendMessage() {
    if (this.isSending() || !this.message()) {
      return;
    }

    this.sendMessage.emit(this.message());
    setTimeout(() => {
      this.message.set('');
    });
  }
}
