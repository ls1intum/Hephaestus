import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideListFilter } from '@ng-icons/lucide';
import { LeaderboardFilterTimeframeComponent } from './timeframe/timeframe.component';
import { LeaderboardFilterTeamComponent } from './team/team.component';

@Component({
  selector: 'app-leaderboard-filter',
  imports: [NgIconComponent, FormsModule, LeaderboardFilterTimeframeComponent, LeaderboardFilterTeamComponent],
  providers: [provideIcons({ lucideListFilter })],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  teams = input<string[]>();
}
