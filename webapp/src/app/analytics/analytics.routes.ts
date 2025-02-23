import { Routes } from '@angular/router';
import { AnalyticsDashboardComponent } from './components/dashboard/analytics-dashboard.component';

export const ANALYTICS_ROUTES: Routes = [
  {
    path: '',
    component: AnalyticsDashboardComponent,
    title: 'Analytics Dashboard'
  }
];
