import { Component, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LeagueEloCardComponent } from '@app/ui/league/elo-card/elo-card.component';
import { LeagueInfoModalComponent } from '@app/ui/league/info-modal/info-modal.component';

@Component({
  selector: 'app-leaderboard-league',
  imports: [HlmCardModule, HlmButtonModule, LeagueEloCardComponent, LeagueInfoModalComponent],
  templateUrl: './league.component.html'
})
export class LeaderboardLeagueComponent {
  leaguePoints = input<number>();
}
