import { Component } from '@angular/core';
import { LeaderboardComponent } from 'app/home/leaderboard/leaderboard.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LeaderboardComponent],
  templateUrl: './home.component.html'
})
export class HomeComponent {}
