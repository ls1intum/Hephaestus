import { Component, computed, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { BrnProgressComponent, BrnProgressIndicatorComponent } from '@spartan-ng/brain/progress';
import { HlmProgressIndicatorDirective } from '@spartan-ng/ui-progress-helm';
import { LeagueIconComponent } from '@app/ui/league/icon/league-icon.component';
import { getLeagueFromPoints } from '@app/utils';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideStar } from '@ng-icons/lucide';

@Component({
  selector: 'app-league-elo-card',
  imports: [HlmCardModule, LeagueIconComponent, BrnProgressComponent, BrnProgressIndicatorComponent, HlmProgressIndicatorDirective, HlmButtonModule, NgIconComponent],
  providers: [provideIcons({ lucideStar })],
  templateUrl: './elo-card.component.html'
})
export class LeagueEloCardComponent {
  protected Infinity = Infinity;
  leaguePoints = input<number>();

  currentLeague = computed(() => getLeagueFromPoints(this.leaguePoints()!));

  progressValue = computed(() => ((this.leaguePoints()! - this.currentLeague()!.minPoints) * 100) / (this.currentLeague()!.maxPoints - this.currentLeague()!.minPoints));
}
