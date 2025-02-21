import { Component, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LeagueEloCardComponent } from '@app/ui/league/elo-card/elo-card.component';
import { LeagueInfoModalComponent } from '@app/ui/league/info-modal/info-modal.component';
import { LeaderboardEntry } from '@app/core/modules/openapi';
import { HlmIconModule } from 'libs/ui/ui-icon-helm/src/index';
import { provideIcons } from '@ng-icons/core';
import { lucideAward, lucideTrophy } from '@ng-icons/lucide';
import { ReviewsPopoverComponent } from '../reviews-popover/reviews-popover.component';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft } from '@ng-icons/octicons';
import { NgIconComponent } from '@ng-icons/core';

@Component({
  selector: 'app-leaderboard-league',
  imports: [HlmCardModule, HlmButtonModule, LeagueEloCardComponent, LeagueInfoModalComponent, HlmIconModule, ReviewsPopoverComponent, NgIconComponent],
  templateUrl: './league.component.html',
  providers: [provideIcons({ lucideAward, lucideTrophy })]
})
export class LeaderboardLeagueComponent {
  protected octFileDiff = octFileDiff;
  protected octCheck = octCheck;
  protected octComment = octComment;
  protected octCommentDiscussion = octCommentDiscussion;
  protected octGitPullRequest = octGitPullRequest;
  protected octChevronLeft = octChevronLeft;
  leaguePoints = input<number>();

  leaderboardEntry = input<LeaderboardEntry>();
}
