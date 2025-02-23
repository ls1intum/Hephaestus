import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReviewMetrics, TeamMetrics, ReviewTrend, QualityMetric, AIInsight, AnalyticsTimeframe } from '../models/analytics.model';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsApiService {
  private readonly baseUrl = '/api/analytics';

  constructor(private http: HttpClient) {}

  getReviewMetrics(timeframe: AnalyticsTimeframe): Observable<ReviewMetrics> {
    return this.http.get<ReviewMetrics>(`${this.baseUrl}/metrics`, { params: { ...timeframe } });
  }

  getTeamMetrics(timeframe: AnalyticsTimeframe): Observable<TeamMetrics[]> {
    return this.http.get<TeamMetrics[]>(`${this.baseUrl}/teams/metrics`, { params: { ...timeframe } });
  }

  getReviewTrends(timeframe: AnalyticsTimeframe): Observable<ReviewTrend[]> {
    return this.http.get<ReviewTrend[]>(`${this.baseUrl}/trends`, { params: { ...timeframe } });
  }

  getQualityMetrics(timeframe: AnalyticsTimeframe): Observable<QualityMetric[]> {
    return this.http.get<QualityMetric[]>(`${this.baseUrl}/quality`, { params: { ...timeframe } });
  }

  getInsights(timeframe: AnalyticsTimeframe): Observable<AIInsight[]> {
    return this.http.get<AIInsight[]>(`${this.baseUrl}/insights`, { params: { ...timeframe } });
  }
}
