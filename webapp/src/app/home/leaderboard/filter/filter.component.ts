import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ListFilter, LucideAngularModule } from 'lucide-angular';
import { LeaderboardFilterTimeframeComponent } from './timeframe/timeframe.component';
import { LeaderboardFilterRepositoryComponent } from './repository/repository.component';

@Component({
  selector: 'app-leaderboard-filter',
  standalone: true,
  imports: [LucideAngularModule, FormsModule, LeaderboardFilterTimeframeComponent, LeaderboardFilterRepositoryComponent],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  protected ListFilter = ListFilter;
  after = input<string>('');
  before = input<string>('');
}
