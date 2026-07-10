import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import {
	getActivityMonitorOptions,
	getUserProfileOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import type { Profile, ProfileActivityMonitor } from "@/api/types.gen";
import {
	type ActivityMonitorFilters,
	DEFAULT_ACTIVITY_MONITOR_LIMIT,
	MAX_ACTIVITY_MONITOR_LIMIT,
} from "@/lib/activity-monitor";
import { type ProviderType, toScmProviderType } from "@/lib/provider";
import {
	DEFAULT_REVIEW_CYCLE,
	formatDateRangeForApi,
	getDateRangeForPreset,
	type ReviewCycleSchedule,
} from "@/lib/timeframe";

/**
 * Search-param schema shared by the developer-profile surfaces (the workspace home self-view and
 * `/user/$username`). Both routes validate against this so the shared {@link useProfileData} hook
 * can read/write the same timeframe + activity-monitor filters.
 */
export const profileSearchSchema = z.object({
	after: z.string().optional(),
	before: z.string().optional(),
	monitorRepositories: z.string().optional(),
	monitorLimit: z.coerce
		.number()
		.int()
		.min(1)
		.max(MAX_ACTIVITY_MONITOR_LIMIT)
		.default(DEFAULT_ACTIVITY_MONITOR_LIMIT),
});

export type ProfileSearchParams = z.infer<typeof profileSearchSchema>;

/**
 * The URL-input shape (before Zod applies defaults). Used as the return type of the search updater,
 * where `monitorLimit` may be omitted to fall back to the default.
 */
export type ProfileSearchInput = z.input<typeof profileSearchSchema>;

export const parseRepositoryIds = (value?: string): number[] => {
	if (!value) return [];

	return value
		.split(",")
		.map((id) => id.trim())
		.filter((id) => /^\d+$/.test(id))
		.map((id) => Number(id))
		.filter((id) => Number.isSafeInteger(id) && id > 0);
};

export const serializeRepositoryIds = (repositoryIds: number[]) => {
	if (repositoryIds.length === 0) return undefined;
	return repositoryIds.join(",");
};

export interface UseProfileDataArgs {
	workspaceSlug: string;
	/** SCM login of the developer whose profile is shown. */
	username: string;
	search: ProfileSearchParams;
	/** Route-scoped search updater (wraps `navigate({ search })`). */
	updateSearch: (updater: (prev: ProfileSearchParams) => ProfileSearchInput) => void;
}

export interface UseProfileDataResult {
	providerType: ProviderType;
	profileData?: Profile;
	activityMonitorData?: ProfileActivityMonitor;
	isLoading: boolean;
	isError: boolean;
	schedule: ReviewCycleSchedule;
	after?: string;
	before?: string;
	activityMonitorFilters: ActivityMonitorFilters;
	handleTimeframeChange: (afterDate: string, beforeDate?: string) => void;
	handleActivityMonitorFiltersChange: (filters: ActivityMonitorFilters) => void;
}

/**
 * Shared data-fetching + search-handling logic for the developer-profile surfaces. Fetches the
 * workspace (for the review-cycle schedule + provider), the user profile, and the activity monitor,
 * and exposes the timeframe/filter handlers. Intentionally does NOT fetch practice reports — those
 * are self-only (`GET /practices/reports/me`) and wired separately by the workspace-home route.
 */
export function useProfileData({
	workspaceSlug,
	username,
	search,
	updateSearch,
}: UseProfileDataArgs) {
	const { after, before, monitorRepositories, monitorLimit } = search;

	const workspaceQuery = useQuery({
		...getWorkspaceOptions({ path: { workspaceSlug } }),
		enabled: Boolean(workspaceSlug),
	});

	// Extract the review-cycle schedule from workspace config.
	const getSchedule = (): ReviewCycleSchedule => {
		if (!workspaceQuery.data) return DEFAULT_REVIEW_CYCLE;

		const scheduledTime = workspaceQuery.data.reviewCycleTime || "9:00";
		const scheduledDay = workspaceQuery.data.reviewCycleDay ?? 2;
		const [hours, minutes] = scheduledTime
			.split(":")
			.map((part: string) => Number.parseInt(part, 10));

		return {
			day: scheduledDay,
			hour: Number.isNaN(hours) ? 9 : hours,
			minute: Number.isNaN(minutes) ? 0 : minutes,
		};
	};
	const schedule = getSchedule();

	// Compute effective date range - default to "this week" based on schedule.
	const getEffectiveDates = () => {
		if (after) {
			return { after, before };
		}
		const range = getDateRangeForPreset("this-week", schedule);
		return formatDateRangeForApi(range);
	};
	const effectiveDates = getEffectiveDates();

	const parseDateParam = (value?: string) => {
		if (!value) return undefined;
		const parsed = new Date(value);
		return Number.isNaN(parsed.getTime()) ? undefined : parsed;
	};
	const parsedAfter = parseDateParam(effectiveDates.after);
	const parsedBefore = parseDateParam(effectiveDates.before);
	const selectedRepositoryIds = parseRepositoryIds(monitorRepositories);

	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: username },
			query: { after: parsedAfter, before: parsedBefore },
		}),
		placeholderData: (previousData) => previousData,
		enabled: Boolean(username) && Boolean(workspaceSlug),
	});

	const activityMonitorQuery = useQuery({
		...getActivityMonitorOptions({
			path: { workspaceSlug, login: username },
			query: {
				after: parsedAfter,
				before: parsedBefore,
				repositoryIds: selectedRepositoryIds.length > 0 ? selectedRepositoryIds : undefined,
				limit: monitorLimit,
			},
		}),
		placeholderData: (previousData) => previousData,
		enabled: Boolean(username) && Boolean(workspaceSlug),
	});

	const handleTimeframeChange = (nextAfter: string, nextBefore?: string) => {
		updateSearch((prev) => ({ ...prev, after: nextAfter, before: nextBefore }));
	};

	const handleActivityMonitorFiltersChange = (filters: ActivityMonitorFilters) => {
		updateSearch((prev) => ({
			...prev,
			monitorRepositories: serializeRepositoryIds(filters.repositoryIds),
			monitorLimit: filters.limit === DEFAULT_ACTIVITY_MONITOR_LIMIT ? undefined : filters.limit,
		}));
	};

	return {
		providerType: toScmProviderType(workspaceQuery.data?.providerType),
		profileData: profileQuery.data,
		activityMonitorData: activityMonitorQuery.data,
		isLoading:
			(profileQuery.isPending && !profileQuery.data) ||
			(workspaceQuery.isPending && !workspaceQuery.data) ||
			(activityMonitorQuery.isPending && !activityMonitorQuery.data),
		isError: profileQuery.isError,
		schedule,
		after: effectiveDates.after,
		before: effectiveDates.before,
		activityMonitorFilters: {
			repositoryIds: selectedRepositoryIds,
			limit: monitorLimit ?? DEFAULT_ACTIVITY_MONITOR_LIMIT,
		},
		handleTimeframeChange,
		handleActivityMonitorFiltersChange,
	};
}
