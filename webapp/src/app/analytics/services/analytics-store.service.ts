import { Injectable, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AnalyticsApiService } from './analytics-api.service';
import { AnalyticsWebSocketService } from './analytics-websocket.service';
import { ReviewMetrics, TeamMetrics, ReviewTrend, QualityMetric, AIInsight, AnalyticsTimeframe } from '../models/analytics.model';

/**
 * Central store for analytics state management.
 * Handles data fetching, caching, and real-time updates for analytics data.
 */
@Injectable({
  providedIn: 'root'
})
export class AnalyticsStoreService {
  // State signals with explicit types
  private readonly metrics = signal<ReviewMetrics | null>(null);
  private readonly teamMetrics = signal<TeamMetrics[]>([]);
  private readonly trends = signal<ReviewTrend[]>([]);
  private readonly quality = signal<QualityMetric[]>([]);
  private readonly insights = signal<AIInsight[]>([]);
  private readonly timeframe = signal<AnalyticsTimeframe>({
    start: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), // Last 30 days
    end: new Date(),
    period: 'month'
  });
  private readonly loading = signal<boolean>(false);
  private readonly error = signal<string | null>(null);

  // Public computed signals with explicit return types
  readonly isLoading = computed<boolean>(() => this.loading());
  readonly currentError = computed<string | null>(() => this.error());
  readonly currentMetrics = computed<ReviewMetrics | null>(() => this.metrics());
  readonly currentTeamMetrics = computed<TeamMetrics[]>(() => this.teamMetrics());
  readonly currentTrends = computed<ReviewTrend[]>(() => this.trends());
  readonly currentQuality = computed<QualityMetric[]>(() => this.quality());
  readonly currentInsights = computed<AIInsight[]>(() => this.insights());
  readonly currentTimeframe = computed<AnalyticsTimeframe>(() => this.timeframe());

  constructor(
    private apiService: AnalyticsApiService,
    private wsService: AnalyticsWebSocketService
  ) {
    this.initializeWebSocket();
  }

  /**
   * Initializes WebSocket connection for real-time updates
   * @private
   */
  private initializeWebSocket(): void {
    this.wsService.connect().pipe(
      takeUntilDestroyed()
    ).subscribe(update => {
      if (update.type === 'metrics') {
        this.metrics.set(update.data as ReviewMetrics);
      } else if (update.type === 'insight') {
        this.insights.update(insights => [update.data as AIInsight, ...insights]);
      }
    });
  }

  /**
   * Loads all analytics data for the specified timeframe
   * @param timeframe Optional timeframe to load data for
   */
  async loadAnalytics(timeframe?: AnalyticsTimeframe): Promise<void> {
    if (timeframe) {
      this.timeframe.set(timeframe);
    }

    this.loading.set(true);
    this.error.set(null);

    try {
      const currentTimeframe = this.timeframe();
      const [metrics, teamMetrics, trends, quality, insights] = await Promise.all([
        this.apiService.getReviewMetrics(currentTimeframe).toPromise(),
        this.apiService.getTeamMetrics(currentTimeframe).toPromise(),
        this.apiService.getReviewTrends(currentTimeframe).toPromise(),
        this.apiService.getQualityMetrics(currentTimeframe).toPromise(),
        this.apiService.getInsights(currentTimeframe).toPromise()
      ]);

      // Update all signals with new data
      this.metrics.set(metrics!);
      this.teamMetrics.set(teamMetrics!);
      this.trends.set(trends!);
      this.quality.set(quality!);
      this.insights.set(insights!);
    } catch (err) {
      this.error.set('Failed to load analytics data');
      console.error('Analytics loading error:', err);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Updates the current timeframe and reloads data
   * @param timeframe New timeframe to set
   */
  updateTimeframe(timeframe: AnalyticsTimeframe): void {
    this.loadAnalytics(timeframe);
  }
}
