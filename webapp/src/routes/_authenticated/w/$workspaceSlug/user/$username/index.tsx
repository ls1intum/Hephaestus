import { useQuery } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { z } from "zod";
import {
	getActivityMonitorOptions,
	getUserProfileOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import type { ActivityMonitorFilters } from "@/components/profile/ProfileContent";
import { ProfilePage } from "@/components/profile/ProfilePage";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";
import {
	DEFAULT_SCHEDULE,
	formatDateRangeForApi,
	getDateRangeForPreset,
	type LeaderboardSchedule,
} from "@/lib/timeframe";

// Default is computed dynamically using the leaderboard schedule
// We don't set a default here because we need the schedule from the workspace
const profileSearchSchema = z.object({
	after: z.string().optional(),
	before: z.string().optional(),
	monitorRepositories: z.string().optional(),
	monitorLimit: z.coerce.number().int().min(1).max(100).default(5),
});

type ProfileSearchParams = z.infer<typeof profileSearchSchema>;

const parseRepositoryIds = (value?: string): number[] => {
	if (!value) return [];

	return value
		.split(",")
		.map((id) => Number.parseInt(id, 10))
		.filter((id) => Number.isFinite(id));
};

const serializeRepositoryIds = (repositoryIds: number[]) => {
	if (repositoryIds.length === 0) return undefined;
	return repositoryIds.join(",");
};

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/user/$username/")({
	component: UserProfile,
	validateSearch: profileSearchSchema,
	search: {
		middlewares: [retainSearchParams(["after", "before", "monitorRepositories", "monitorLimit"])],
	},
});

function UserProfile() {
	const { username, workspaceSlug } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const { achievementsEnabled, progressionEnabled, leaguesEnabled } = useWorkspaceFeatures();
	const { after, before, monitorRepositories, monitorLimit } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Query for workspace to get leaderboard schedule
	const workspaceQuery = useQuery({
		...getWorkspaceOptions({
			path: { workspaceSlug },
		}),
		enabled: Boolean(workspaceSlug),
	});

	// Extract leaderboard schedule from workspace config
	// React Compiler handles memoization automatically
	const getSchedule = (): LeaderboardSchedule => {
		if (!workspaceQuery.data) return DEFAULT_SCHEDULE;

		const scheduledTime = workspaceQuery.data.leaderboardScheduleTime || "9:00";
		const scheduledDay = workspaceQuery.data.leaderboardScheduleDay ?? 2;
		const [hours, minutes] = scheduledTime
			.split(":")
			.map((part: string) => Number.parseInt(part, 10));

		return {
			day: scheduledDay,
			hour: hours || 9,
			minute: minutes || 0,
		};
	};
	const schedule = getSchedule();

	// Compute effective date range - default to "this week" based on schedule
	const getEffectiveDates = () => {
		if (after) {
			return { after, before };
		}
		// Default to "this week" using the leaderboard schedule
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

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for user profile data
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: username },
			query: {
				after: parsedAfter,
				before: parsedBefore,
			},
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
		navigate({
			search: (prev: ProfileSearchParams) => ({
				...prev,
				after: nextAfter,
				before: nextBefore,
			}),
		});
	};

	const handleActivityMonitorFiltersChange = (filters: ActivityMonitorFilters) => {
		navigate({
			search: (prev: ProfileSearchParams) => ({
				...prev,
				monitorRepositories: serializeRepositoryIds(filters.repositoryIds),
				monitorLimit: filters.limit === 5 ? undefined : filters.limit,
			}),
		});
	};

	return (
		<ProfilePage
			providerType={workspaceQuery.data?.providerType ?? "GITHUB"}
			profileData={profileQuery.data}
			activityMonitorData={activityMonitorQuery.data}
			activityMonitorFilters={{
				repositoryIds: selectedRepositoryIds,
				limit: monitorLimit,
			}}
			onActivityMonitorFiltersChange={handleActivityMonitorFiltersChange}
			isLoading={
				(profileQuery.isPending && !profileQuery.data) ||
				(workspaceQuery.isPending && !workspaceQuery.data)
			}
			error={profileQuery.isError}
			username={username}
			currUserIsDashboardUser={currUserIsDashboardUser}
			workspaceSlug={workspaceSlug}
			after={effectiveDates.after}
			before={effectiveDates.before}
			onTimeframeChange={handleTimeframeChange}
			schedule={schedule}
			achievementsEnabled={achievementsEnabled}
			progressionEnabled={progressionEnabled}
			leaguesEnabled={leaguesEnabled}
		/>
	);
}
