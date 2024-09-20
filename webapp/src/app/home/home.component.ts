import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LeaderboardService } from 'app/core/modules/openapi/api/leaderboard.service';
import { LeaderboardComponent } from 'app/home/leaderboard/leaderboard.component';
import { lastValueFrom } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LeaderboardComponent],
  templateUrl: './home.component.html'
})
export class HomeComponent {
  leaderboardService = inject(LeaderboardService);

  // timeframe for leaderboard
  // example: 2024-09-19T10:15:30+01:00
  private readonly route = inject(ActivatedRoute);
  private queryParams = toSignal(this.route.queryParamMap, { requireSync: true });
  protected after = computed(() => this.queryParams().get('after')?.replace(' ', '+') ?? undefined);
  protected before = computed(() => this.queryParams().get('before')?.replace(' ', '+') ?? undefined);

  query = injectQuery(() => ({
    queryKey: ['leaderboard', { after: this.after, before: this.before }],
    queryFn: async () => lastValueFrom(this.leaderboardService.getLeaderboard(this.after(), this.before())),
    gcTime: Infinity
  }));
}
