import { Component, computed, inject, input, signal } from '@angular/core';
import { LeaderboardEntry, LeaderboardService } from '@app/core/modules/openapi';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LeagueEloCardComponent } from '@app/ui/league/elo-card/elo-card.component';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { NgIconComponent } from '@ng-icons/core';
import { provideIcons } from '@ng-icons/core';
import { lucideClock, lucideTrendingUp, lucideTrendingDown, lucideMoveRight } from '@ng-icons/lucide';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import dayjs, { Dayjs } from 'dayjs/esm';

@Component({
  selector: 'app-leaderboard-overview',
  imports: [HlmCardModule, HlmButtonModule, HlmAvatarModule, LeagueEloCardComponent, NgIconComponent],
  templateUrl: './leaderboard-overview.component.html',
  providers: [provideIcons({ lucideClock, lucideTrendingUp, lucideTrendingDown, lucideMoveRight })]
})
export class LeaderboardOverviewComponent {
  private readonly route = inject(ActivatedRoute);
  leaderboardEntry = input.required<LeaderboardEntry>();
  leaguePoints = input.required<number>();
  leaderboardService = inject(LeaderboardService);

  leaderboardEnd = signal<Dayjs>(dayjs());
  leaderboardTimeUntilEnd = computed(() => {
    const diff = this.leaderboardEnd().diff(dayjs(), 'hours');
    if (diff < 0) {
      return 'Ended';
    }
    if (diff > 24) {
      return `${Math.floor(diff / 24)}d ${diff % 24}h`;
    }
    return `${diff}h`;
  });

  leagueChangeQuery = injectQuery(() => ({
    queryKey: ['league', 'change', this.leaderboardEntry().user.login],
    queryFn: async () => lastValueFrom(this.leaderboardService.getUserLeagueStats(this.leaderboardEntry().user.login, this.leaderboardEntry())),
    select: (data) => data.leaguePointsChange
  }));

  constructor() {
    this.route.queryParams.subscribe((params) => {
      if (params['before']) {
        this.leaderboardEnd.set(dayjs(params['before']));
      }
    });
  }

  scrollToRank(rank: number) {
    const element = document.getElementById(`rank-${rank}`);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
