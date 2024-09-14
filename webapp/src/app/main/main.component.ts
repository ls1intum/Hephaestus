import { Component } from '@angular/core';
import { LeaderboardComponent } from 'app/components/leaderboard/leaderboard.component';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [LeaderboardComponent],
  templateUrl: './main.component.html'
})
export class MainComponent {}
