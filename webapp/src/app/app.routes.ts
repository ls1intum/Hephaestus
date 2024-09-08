import { Routes } from '@angular/router';
import { AboutComponent } from 'app/about/about.component';
import { MainComponent } from 'app/main/main.component';
import { LeaderboardComponent } from './leaderboard/leaderboard.component';

export const routes: Routes = [
  { path: '', component: MainComponent },
  { path: 'about', component: AboutComponent },
  { path: 'leaderboard', component: LeaderboardComponent }
];
