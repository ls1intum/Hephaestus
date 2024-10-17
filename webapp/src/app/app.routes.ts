import { Routes } from '@angular/router';
import { AboutComponent } from '@app/about/about.component';
import { HomeComponent } from '@app/home/home.component';
import { AdminComponent } from '@app/admin/admin.component';
import { AdminGuard } from '@app/core/security/admin.guard';
import { UserProfileComponent } from '@app/user/user-profile.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AdminGuard]
  },
  { path: 'user/:id', component: UserProfileComponent }
];
