export const DEFAULT_ACTIVITY_MONITOR_LIMIT = 5;
export const MAX_ACTIVITY_MONITOR_LIMIT = 100;

export interface ActivityMonitorFilters {
	repositoryIds: number[];
	limit: number;
}
