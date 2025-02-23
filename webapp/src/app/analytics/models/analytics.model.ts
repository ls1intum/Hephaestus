/**
 * Core metrics for code review activity
 */
export interface ReviewMetrics {
  /** Total number of reviews in the period */
  totalReviews: number;
  /** Average time to first response in hours */
  averageResponseTime: number;
  /** Percentage of reviews completed */
  completionRate: number;
  /** Overall quality score out of 100 */
  qualityScore: number;
}

/**
 * Team-specific review metrics
 */
export interface TeamMetrics extends ReviewMetrics {
  /** Unique identifier for the team */
  teamId: string;
  /** Display name of the team */
  teamName: string;
  /** Number of active team members */
  memberCount: number;
}

/**
 * Historical trend data point for reviews
 */
export interface ReviewTrend {
  /** ISO date string */
  date: string;
  /** Number of reviews for this date */
  count: number;
  /** Average response time in hours */
  averageTime: number;
}

/**
 * Individual quality metric with trend
 */
export interface QualityMetric {
  /** Name of the quality category */
  category: string;
  /** Score out of 100 */
  score: number;
  /** Current trend direction */
  trend: 'up' | 'down' | 'stable';
}

/**
 * AI-generated insight about review patterns
 */
export interface AIInsight {
  /** Unique identifier for the insight */
  id: string;
  /** Type of insight for display purposes */
  type: 'improvement' | 'warning' | 'achievement';
  /** Human-readable insight message */
  message: string;
  /** Related metric values */
  metrics: Record<string, number>;
  /** ISO timestamp when the insight was generated */
  timestamp: string;
}

/**
 * Time range for analytics queries
 */
export interface AnalyticsTimeframe {
  /** Start date of the range */
  start: Date;
  /** End date of the range */
  end: Date;
  /** Aggregation period */
  period: 'day' | 'week' | 'month' | 'quarter' | 'year';
}
