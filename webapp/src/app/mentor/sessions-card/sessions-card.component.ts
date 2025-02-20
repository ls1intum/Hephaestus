import { Component, input, model, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucidePlus } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnToggleDirective } from '@spartan-ng/brain/toggle';
import { Session } from '@app/core/modules/openapi';
import { HlmToggleDirective } from '@spartan-ng/ui-toggle-helm';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

@Component({
  selector: 'app-sessions-card',
  templateUrl: './sessions-card.component.html',
  imports: [CommonModule, HlmSkeletonComponent, NgIconComponent, BrnToggleDirective, HlmToggleDirective, HlmButtonModule, HlmCardDirective],
  providers: [provideIcons({ lucidePlus })]
})
export class SessionsCardComponent {
  sessions = input<Session[]>();
  selectedSessionId = model<number | null>();
  createNewSession = output<void>();
  isLoading = input<boolean>();
}
