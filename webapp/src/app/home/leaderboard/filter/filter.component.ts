import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ListFilter, LucideAngularModule } from 'lucide-angular';
import { LeaderboardFilterTimeframeComponent } from './timeframe/timeframe.component';
import { LeaderboardFilterTeamComponent } from './team/team.component';

@Component({
  selector: 'app-leaderboard-filter',
  standalone: true,
  imports: [LucideAngularModule, FormsModule, LeaderboardFilterTimeframeComponent, LeaderboardFilterTeamComponent],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  protected ListFilter = ListFilter;

  teams = input<string[]>();
}
