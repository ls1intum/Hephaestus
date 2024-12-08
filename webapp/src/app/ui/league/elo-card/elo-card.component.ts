import { Component, computed, input, signal } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { BrnProgressComponent, BrnProgressIndicatorComponent } from '@spartan-ng/ui-progress-brain';
import { HlmProgressIndicatorDirective } from '@spartan-ng/ui-progress-helm';
import { LeagueIconComponent } from '@app/ui/league/icon/league-icon.component';
import { getLeagueFromPoints } from '@app/utils';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LucideAngularModule, Star } from 'lucide-angular';

@Component({
  selector: 'app-league-elo-card',
  standalone: true,
  imports: [
    HlmCardModule,
    LeagueIconComponent,
    BrnProgressComponent,
    BrnProgressIndicatorComponent,
    HlmProgressIndicatorDirective,
    HlmButtonModule,
    LucideAngularModule
  ],
  templateUrl: './elo-card.component.html'
})
export class LeagueEloCardComponent {
  protected Star = Star;
  protected Infinity = Infinity;
  leaguePoints = input<number>();

  currentLeague = computed(() => getLeagueFromPoints(this.leaguePoints()!));

  progressValue = signal(0);

  ngOnInit() {
    if (this.leaguePoints() && this.currentLeague()) {
      this.progressValue.set((this.leaguePoints()! - this.currentLeague()!.minPoints) * 100 / (this.currentLeague()!.maxPoints - this.currentLeague()!.minPoints));
    }
  }
}
