import { Component, input } from '@angular/core';
import { LeaderboardEntry } from '@app/core/modules/openapi';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LeagueEloCardComponent } from '@app/ui/league/elo-card/elo-card.component';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';

@Component({
  selector: 'app-leaderboard-overview',
  imports: [HlmCardModule, HlmButtonModule, HlmAvatarModule, LeagueEloCardComponent],
  templateUrl: './leaderboard-overview.component.html'
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
