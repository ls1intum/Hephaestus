import { Component, inject } from '@angular/core';
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

  query = injectQuery(() => ({
    queryKey: ['leaderboard'],
    queryFn: async () => lastValueFrom(this.leaderboardService.getLeaderboard()),
    gcTime: Infinity
  }));
}
