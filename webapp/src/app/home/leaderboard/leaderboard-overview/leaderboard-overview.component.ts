import { Component, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { provideIcons } from '@ng-icons/core';
import { lucideAward, lucideTrophy } from '@ng-icons/lucide';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft } from '@ng-icons/octicons';
import { LeaderboardEntry } from '@app/core/modules/openapi';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LeagueEloCardComponent } from '@app/ui/league/elo-card/elo-card.component';
import { LeagueInfoModalComponent } from '@app/ui/league/info-modal/info-modal.component';
import { HlmIconModule } from '@spartan-ng/ui-icon-helm';
import { ReviewsPopoverComponent } from '../reviews-popover/reviews-popover.component';

@Component({
  selector: 'app-leaderboard-overview',
  imports: [HlmCardModule, HlmButtonModule, LeagueEloCardComponent, LeagueInfoModalComponent, HlmIconModule, ReviewsPopoverComponent, NgIconComponent],
  templateUrl: './leaderboard-overview.component.html',
  providers: [provideIcons({ lucideAward, lucideTrophy, octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft })]
})
export class LeaderboardOverviewComponent {
  leaderboardEntry = input.required<LeaderboardEntry>();
  leaguePoints = input.required<number>();
}
