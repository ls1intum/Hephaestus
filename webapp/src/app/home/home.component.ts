import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LeaderboardService } from 'app/core/modules/openapi/api/leaderboard.service';
import { LeaderboardComponent } from 'app/home/leaderboard/leaderboard.component';
import { lastValueFrom } from 'rxjs';

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
  protected after: string | undefined = undefined;
  protected before: string | undefined = undefined;

  constructor() {
    inject(ActivatedRoute).queryParamMap.subscribe((params) => {
      this.after = params.get('after')?.replace(' ', '+') ?? undefined;
      this.before = params.get('before')?.replace(' ', '+') ?? undefined;
    });
  }

  query = injectQuery(() => ({
    queryKey: ['leaderboard'],
    queryFn: async () => lastValueFrom(this.leaderboardService.getLeaderboard(this.before, this.after)),
    gcTime: Infinity
  }));
}
