import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LeaderboardService } from 'app/core/modules/openapi/api/leaderboard.service';
import { LeaderboardComponent } from 'app/home/leaderboard/leaderboard.component';
import { combineLatest, timer, lastValueFrom, map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { LeaderboardFilterComponent } from './leaderboard/filter/filter.component';
import dayjs from 'dayjs';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LeaderboardComponent, LeaderboardFilterComponent],
  templateUrl: './home.component.html'
})
export class HomeComponent {
  leaderboardService = inject(LeaderboardService);

  private readonly route = inject(ActivatedRoute);
  private queryParams = toSignal(this.route.queryParamMap, { requireSync: true });
  // leaderboard filter
  protected after = computed(() => this.queryParams().get('after') ?? dayjs().day(1).format('YYYY-MM-DD'));
  protected before = computed(() => this.queryParams().get('before') ?? dayjs().format('YYYY-MM-DD'));
  protected repository = computed(() => this.queryParams().get('repository') ?? 'all');

  query = injectQuery(() => ({
    queryKey: ['leaderboard', { after: this.after(), before: this.before(), repository: this.repository() }],
    queryFn: async () =>
      lastValueFrom(
        combineLatest([this.leaderboardService.getLeaderboard(this.after(), this.before(), this.repository() !== 'all' ? this.repository() : undefined), timer(500)]).pipe(
          map(([leaderboard]) => leaderboard)
        )
      )
  }));
}
