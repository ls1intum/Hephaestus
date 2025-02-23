import { Component, input } from '@angular/core';
import { provideIcons } from '@ng-icons/core';
import { lucideAward } from '@ng-icons/lucide';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft } from '@ng-icons/octicons';
import { LeaderboardEntry } from '@app/core/modules/openapi';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LeagueEloCardComponent } from '@app/ui/league/elo-card/elo-card.component';
import { HlmIconModule } from '@spartan-ng/ui-icon-helm';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';

@Component({
  selector: 'app-leaderboard-overview',
  imports: [HlmCardModule, HlmButtonModule, HlmAvatarModule, LeagueEloCardComponent, HlmIconModule],
  templateUrl: './leaderboard-overview.component.html',
  providers: [provideIcons({ lucideAward, octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft })]
})
export class LeaderboardOverviewComponent {
  leaderboardEntry = input.required<LeaderboardEntry>();
  leaguePoints = input.required<number>();

  scrollToRank(rank: number) {
    const element = document.getElementById(`rank-${rank}`);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
