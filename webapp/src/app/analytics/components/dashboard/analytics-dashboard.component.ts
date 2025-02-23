import { Component, OnInit } from '@angular/core';
import { HlmCardComponent, HlmCardContentDirective, HlmCardHeaderDirective } from '@spartan-ng/ui-card-helm';
import { MetricsViewComponent } from '../metrics/metrics-view.component';
import { InsightsPanelComponent } from '../insights/insights-panel.component';
import { AnalyticsStoreService } from '../../services/analytics-store.service';
import { HlmSpinnerComponent } from '@spartan-ng/ui-spinner-helm';
import { HlmAlertComponent } from '@spartan-ng/ui-alert-helm';

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [
    HlmCardComponent,
    HlmCardHeaderDirective,
    HlmCardContentDirective,
    MetricsViewComponent,
    InsightsPanelComponent,
    HlmSpinnerComponent,
    HlmAlertComponent
  ],
  template: `
    <div class="container mx-auto p-4 space-y-6">
      <div class="flex justify-between items-center">
        <h1 class="text-3xl font-bold">Analytics Dashboard</h1>
        
        <!-- Loading indicator -->
        @if (store.isLoading()) {
          <hlm-spinner />
        }
      </div>

      <!-- Error alert -->
      @if (store.currentError()) {
        <hlm-alert variant="destructive">
          {{ store.currentError() }}
        </hlm-alert>
      }
      
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <!-- Overview Cards -->
        <hlm-card>
          <header hlmCardHeader>
            <h3 class="text-lg font-semibold">Review Activity</h3>
          </header>
          <div hlmCardContent>
            @if (store.currentMetrics()) {
              <div class="space-y-2">
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Total Reviews</span>
                  <span class="font-medium">{{ store.currentMetrics()?.totalReviews }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Completion Rate</span>
                  <span class="font-medium">{{ store.currentMetrics()?.completionRate }}%</span>
                </div>
              </div>
            }
          </div>
        </hlm-card>

        <hlm-card>
          <header hlmCardHeader>
            <h3 class="text-lg font-semibold">Response Times</h3>
          </header>
          <div hlmCardContent>
            @if (store.currentMetrics()) {
              <div class="space-y-2">
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Average Response</span>
                  <span class="font-medium">{{ store.currentMetrics()?.averageResponseTime }}h</span>
                </div>
              </div>
            }
          </div>
        </hlm-card>

        <hlm-card>
          <header hlmCardHeader>
            <h3 class="text-lg font-semibold">Quality Metrics</h3>
          </header>
          <div hlmCardContent>
            @if (store.currentMetrics()) {
              <div class="space-y-2">
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Quality Score</span>
                  <span class="font-medium">{{ store.currentMetrics()?.qualityScore }}/100</span>
                </div>
              </div>
            }
          </div>
        </hlm-card>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2">
          <app-metrics-view />
        </div>
        <div>
          <app-insights-panel />
        </div>
      </div>
    </div>
  `
})
export class AnalyticsDashboardComponent implements OnInit {
  constructor(public store: AnalyticsStoreService) {}

  ngOnInit() {
    this.store.loadAnalytics();
  }
}
