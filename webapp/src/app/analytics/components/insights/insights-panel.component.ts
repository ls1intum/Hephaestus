import { Component } from '@angular/core';
import { HlmCardComponent, HlmCardContentDirective, HlmCardHeaderDirective } from '@spartan-ng/ui-card-helm';
import { AnalyticsStoreService } from '../../services/analytics-store.service';
import { NgClass } from '@angular/common';

@Component({
  selector: 'app-insights-panel',
  standalone: true,
  imports: [HlmCardComponent, HlmCardHeaderDirective, HlmCardContentDirective, NgClass],
  template: `
    <hlm-card>
      <header hlmCardHeader>
        <h3 class="text-lg font-semibold">AI Insights</h3>
      </header>
      <div hlmCardContent>
        <div class="space-y-4">
          @if (!store.currentInsights()?.length) {
            <div class="animate-pulse">
              <div class="h-4 bg-gray-200 rounded w-3/4"></div>
              <div class="space-y-3 mt-4">
                <div class="h-4 bg-gray-200 rounded"></div>
                <div class="h-4 bg-gray-200 rounded w-5/6"></div>
              </div>
            </div>
          } @else {
            @for (insight of store.currentInsights(); track insight.id) {
              <div class="p-4 rounded-lg" [ngClass]="{
                'bg-blue-50 border border-blue-200': insight.type === 'improvement',
                'bg-yellow-50 border border-yellow-200': insight.type === 'warning',
                'bg-green-50 border border-green-200': insight.type === 'achievement'
              }">
                <p class="text-sm">{{ insight.message }}</p>
                @if (insight.metrics) {
                  <div class="mt-2 text-xs text-muted-foreground">
                    @for (metric of insight.metrics | keyvalue; track metric.key) {
                      <span class="inline-block mr-4">
                        {{ metric.key }}: {{ metric.value }}
                      </span>
                    }
                  </div>
                }
                <div class="mt-2 text-xs text-muted-foreground">
                  {{ insight.timestamp | date:'medium' }}
                </div>
              </div>
            }
          }
        </div>
      </div>
    </hlm-card>
  `
})
export class InsightsPanelComponent {
  constructor(public store: AnalyticsStoreService) {}
}
