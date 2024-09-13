import { Component } from '@angular/core';
import { LeaderboardComponent } from 'app/components/leaderboard/leaderboard.component';
import { HelloComponent } from 'app/example/hello/hello.component';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [HelloComponent, LeaderboardComponent],
  templateUrl: './main.component.html'
})
export class MainComponent {}
