import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LeaderboardService } from 'app/core/modules/openapi/api/leaderboard.service';
import { LeaderboardComponent } from 'app/home/leaderboard/leaderboard.component';
import { delay, lastValueFrom } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { LeaderboardFilterComponent } from './leaderboard/filter/filter.component';
import { SkeletonComponent } from 'app/ui/skeleton/skeleton.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LeaderboardComponent, LeaderboardFilterComponent, SkeletonComponent],
  templateUrl: './home.component.html'
})
export class HomeComponent {
  leaderboardService = inject(LeaderboardService);

  // timeframe for leaderboard
  // example: 2024-09-19
  private readonly route = inject(ActivatedRoute);
  private queryParams = toSignal(this.route.queryParamMap, { requireSync: true });
  protected after = computed(() => this.queryParams().get('after') ?? undefined);
  protected before = computed(() => this.queryParams().get('before') ?? undefined);

  query = injectQuery(() => ({
    queryKey: ['leaderboard', { after: this.after(), before: this.before() }],
    queryFn: async () => lastValueFrom(this.leaderboardService.getLeaderboard(this.after(), this.before()).pipe(delay(1000)))
  }));
}
