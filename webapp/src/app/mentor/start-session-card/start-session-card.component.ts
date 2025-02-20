import { Component, input, output } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucidePlus, lucideBotMessageSquare } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

@Component({
  selector: 'app-start-session-card',
  templateUrl: './start-session-card.component.html',
  imports: [NgIconComponent, HlmButtonModule, HlmSkeletonComponent],
  providers: [provideIcons({ lucidePlus, lucideBotMessageSquare })]
})
export class StartSessionCardComponent {
  createNewSession = output<void>();
  isLoading = input<boolean>();
  hasSessions = input<boolean>(false);
}
