import { Component, input, model, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Plus } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnToggleDirective } from '@spartan-ng/ui-toggle-brain';
import { Session } from '@app/core/modules/openapi';
import { HlmToggleDirective } from '@spartan-ng/ui-toggle-helm';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

@Component({
    selector: 'app-sessions-card',
    templateUrl: './sessions-card.component.html',
    imports: [CommonModule, HlmSkeletonComponent, LucideAngularModule, BrnToggleDirective, HlmToggleDirective, HlmButtonModule, HlmCardDirective]
})
export class SessionsCardComponent {
  protected Plus = Plus;

  sessions = input<Session[]>();
  selectedSessionId = model<number | null>();
  createNewSession = output<void>();
  isLoading = input<boolean>();
}
