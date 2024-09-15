import { Routes } from '@angular/router';
import { AboutComponent } from 'app/about/about.component';
import { HomeComponent } from 'app/home/home.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent }
];
