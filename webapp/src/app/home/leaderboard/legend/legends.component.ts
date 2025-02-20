import { Component, input } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest } from '@ng-icons/octicons';
import { HlmIconDirective } from '@spartan-ng/ui-icon-helm';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import {
  HlmAccordionContentComponent,
  HlmAccordionDirective,
  HlmAccordionIconDirective,
  HlmAccordionItemDirective,
  HlmAccordionTriggerDirective
} from '@spartan-ng/ui-accordion-helm';

@Component({
  selector: 'app-leaderboard-legend',
  imports: [
    HlmAccordionDirective,
    HlmAccordionItemDirective,
    HlmAccordionTriggerDirective,
    HlmAccordionContentComponent,
    HlmAccordionIconDirective,
    HlmCardModule,
    NgIconComponent,
    HlmIconDirective
  ],
  providers: [provideIcons({ octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest })],
  templateUrl: './legend.component.html'
})
export class LeaderboardLegendComponent {
  isLoading = input<boolean>();
}
