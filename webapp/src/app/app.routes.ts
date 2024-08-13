import { Routes } from '@angular/router';
import { AboutComponent } from 'app/about/about.component';
import { MainComponent } from 'app/main/main.component';

export const routes: Routes = [
  { path: '', component: MainComponent },
  { path: 'about', component: AboutComponent }
];
