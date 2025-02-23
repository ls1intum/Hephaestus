import { ChartConfiguration } from 'chart.js';

/**
 * Configuration for trend chart display
 */
export interface TrendChartConfig extends ChartConfiguration {
  options: ChartConfiguration['options'] & {
    responsive: true;
    maintainAspectRatio: false;
    interaction: {
      intersect: false;
      mode: 'index';
    };
  };
}

/**
 * Configuration for quality metrics radar chart
 */
export interface QualityChartConfig extends ChartConfiguration {
  options: ChartConfiguration['options'] & {
    responsive: true;
    maintainAspectRatio: false;
  };
}
