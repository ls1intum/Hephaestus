import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { HlmCardComponent, HlmCardContentDirective, HlmCardHeaderDirective } from '@spartan-ng/ui-card-helm';
import { AnalyticsStoreService } from '../../services/analytics-store.service';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { Subject, takeUntil } from 'rxjs';

Chart.register(...registerables);

@Component({
  selector: 'app-metrics-view',
  standalone: true,
  imports: [HlmCardComponent, HlmCardHeaderDirective, HlmCardContentDirective],
  template: `
    <hlm-card>
      <header hlmCardHeader>
        <h3 class="text-lg font-semibold">Detailed Metrics</h3>
      </header>
      <div hlmCardContent>
        <div class="space-y-4">
          <!-- Main trend chart -->
          <div class="h-[400px]">
            <canvas #trendChart></canvas>
          </div>
          
          <!-- Quality metrics chart -->
          <div class="h-[300px]">
            <canvas #qualityChart></canvas>
          </div>
        </div>
      </div>
    </hlm-card>
  `
})
export class MetricsViewComponent implements AfterViewInit, OnDestroy {
  @ViewChild('trendChart') trendChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('qualityChart') qualityChartRef!: ElementRef<HTMLCanvasElement>;

  private trendChart?: Chart;
  private qualityChart?: Chart;
  private destroy$ = new Subject<void>();

  constructor(private store: AnalyticsStoreService) {}

  ngAfterViewInit() {
    this.initTrendChart();
    this.initQualityChart();
  }

  private initTrendChart() {
    const ctx = this.trendChartRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const config: ChartConfiguration = {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Review Count',
            data: [],
            borderColor: 'rgb(59, 130, 246)',
            tension: 0.1
          },
          {
            label: 'Average Response Time',
            data: [],
            borderColor: 'rgb(139, 92, 246)',
            tension: 0.1
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          intersect: false,
          mode: 'index'
        }
      }
    };

    this.trendChart = new Chart(ctx, config);

    // Subscribe to trend updates
    this.store.currentTrends.pipe(
      takeUntil(this.destroy$)
    ).subscribe(trends => {
      if (this.trendChart) {
        this.trendChart.data.labels = trends.map(t => t.date);
        this.trendChart.data.datasets[0].data = trends.map(t => t.count);
        this.trendChart.data.datasets[1].data = trends.map(t => t.averageTime);
        this.trendChart.update();
      }
    });
  }

  private initQualityChart() {
    const ctx = this.qualityChartRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const config: ChartConfiguration = {
      type: 'radar',
      data: {
        labels: [],
        datasets: [{
          label: 'Quality Metrics',
          data: [],
          backgroundColor: 'rgba(59, 130, 246, 0.2)',
          borderColor: 'rgb(59, 130, 246)',
          pointBackgroundColor: 'rgb(59, 130, 246)'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false
      }
    };

    this.qualityChart = new Chart(ctx, config);

    // Subscribe to quality metrics updates
    this.store.currentQuality.pipe(
      takeUntil(this.destroy$)
    ).subscribe(metrics => {
      if (this.qualityChart) {
        this.qualityChart.data.labels = metrics.map(m => m.category);
        this.qualityChart.data.datasets[0].data = metrics.map(m => m.score);
        this.qualityChart.update();
      }
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.trendChart?.destroy();
    this.qualityChart?.destroy();
  }
}
