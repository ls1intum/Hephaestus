import { useQuery } from "@tanstack/react-query";
import {
	createFileRoute,
	retainSearchParams,
	useNavigate,
} from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import {
	getUserProfileOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import { ProfilePage } from "@/components/profile/ProfilePage";
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
});

type ProfileSearchParams = z.infer<typeof profileSearchSchema>;

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/",
)({
	component: UserProfile,
	validateSearch: zodValidator(profileSearchSchema),
	search: {
		middlewares: [retainSearchParams(["after", "before"])],
	},
});

function UserProfile() {
	const { username, workspaceSlug } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const { after, before } = Route.useSearch();
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

	// Check if current user is the dashboard user
	const currUserIsDashboardUser = isCurrentUser(username);

	// Query for user profile data
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: username },
			query: {
				after: parseDateParam(effectiveDates.after),
				before: parseDateParam(effectiveDates.before),
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

	return (
		<ProfilePage
			profileData={profileQuery.data}
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
		/>
	);
}
