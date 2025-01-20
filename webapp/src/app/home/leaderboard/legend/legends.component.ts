import { Component, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest } from '@ng-icons/octicons';
import { HlmIconComponent } from '@spartan-ng/ui-icon-helm';
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
    HlmIconComponent
  ],
  templateUrl: './legend.component.html'
})
export class LeaderboardLegendComponent {
  protected octFileDiff = octFileDiff;
  protected octCheck = octCheck;
  protected octComment = octComment;
  protected octCommentDiscussion = octCommentDiscussion;
  protected octGitPullRequest = octGitPullRequest;

  isLoading = input<boolean>();
}
