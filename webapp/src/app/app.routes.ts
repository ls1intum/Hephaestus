import { Routes } from '@angular/router';
import { AboutComponent } from '@app/about/about.component';
import { HomeComponent } from '@app/home/home.component';
import { AdminComponent } from '@app/admin/admin.component';
import { AdminGuard } from '@app/core/security/admin.guard';
import { UserProfileComponent } from '@app/user/user-profile.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'Home', data: { meta: { description: 'Artemis Leaderboard - Hephaestus' } } },
  { path: 'about', component: AboutComponent, title: 'About', data: { meta: { description: 'About Hephaestus' } } },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AdminGuard],
    title: 'Admin'
  },
  { path: 'user/:id', component: UserProfileComponent, data: { meta: { description: 'User Profile - Hephaestus' } } }
];
