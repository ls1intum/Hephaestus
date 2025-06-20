import {
	getLeaderboardOptions,
	getMetaDataOptions,
	getUserLeagueStatsOptions,
	getUserProfileOptions,
} from "@/api/@tanstack/react-query.gen";
import { LeaderboardPage } from "@/components/leaderboard/LeaderboardPage";
import type { LeaderboardSortType } from "@/components/leaderboard/SortFilter";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams } from "@tanstack/react-router";
import { useNavigate } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { endOfISOWeek, formatISO, startOfISOWeek } from "date-fns";
import { useMemo } from "react";
import { z } from "zod";

// Calculate default date range with ISO 8601 format including timezone
const today = new Date();
const startOfCurrentWeekDate = startOfISOWeek(today);
const endOfCurrentWeekDate = endOfISOWeek(today);
const startOfCurrentWeek = formatISO(startOfCurrentWeekDate);
const endOfCurrentWeek = formatISO(endOfCurrentWeekDate);

// Define search params schema for validation and types
const leaderboardSearchSchema = z.object({
	team: z.string().default("all"),
	sort: z.enum(["SCORE", "LEAGUE_POINTS"]).default("SCORE"),
	after: z.string().optional().default(startOfCurrentWeek),
	before: z.string().optional().default(endOfCurrentWeek),
});

// Export route with search param validation
export const Route = createFileRoute("/_authenticated/")({
	component: LeaderboardContainer,
	validateSearch: zodValidator(leaderboardSearchSchema),
	// Configure search middleware to retain params when navigating
	search: {
		middlewares: [retainSearchParams(["team", "sort", "after", "before"])],
	},
});

function LeaderboardContainer() {
	// Get the current user from auth context
	const { username } = useAuth();

	// Access properly validated search params with correct types
	const { team, sort, after, before } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Query for metadata (teams, schedule info)
	const metaQuery = useQuery({
		...getMetaDataOptions({}),
	});

	// Query for leaderboard data based on filters
	const leaderboardQuery = useQuery({
		...getLeaderboardOptions({
			query: {
				after: new Date(after || startOfCurrentWeek),
				before: new Date(before || endOfCurrentWeek),
				team,
				sort,
			},
		}),
		enabled: Boolean(after && before && metaQuery.data),
	});

	// Query for user profile data
	const userProfileQuery = useQuery({
		...getUserProfileOptions({
			path: { login: username || "" },
		}),
		enabled: Boolean(username),
	});
	// Find the current user's entry in the leaderboard
	const currentUserEntry = username
		? leaderboardQuery.data?.find(
				(entry) => entry.user.login.toLowerCase() === username.toLowerCase(),
			)
		: undefined;

	// Get the leaderboard schedule from the server's metadata
	const leaderboardSchedule = useMemo(() => {
		// Parse the scheduled time and day from the metadata
		const scheduledTime = metaQuery.data?.scheduledTime || "9:00";
		const scheduledDay = Number.parseInt(
			metaQuery.data?.scheduledDay || "2",
			10,
		);
		const [hours, minutes] = scheduledTime
			.split(":")
			.map((part) => Number.parseInt(part, 10));

		return {
			day: scheduledDay,
			hour: hours || 9,
			minute: minutes || 0,
		};
	}, [metaQuery.data]);

	// Calculate leaderboard end date with the correct time
	const leaderboardEnd = useMemo(() => {
		const endDate = new Date(before);

		// Adjust the end date to include the schedule time from server metadata
		endDate.setHours(
			leaderboardSchedule.hour,
			leaderboardSchedule.minute,
			0,
			0,
		);

		return formatISO(endDate);
	}, [before, leaderboardSchedule]);

	// Query for league points change data if we have a current user entry
	const leagueStatsQuery = useQuery({
		...getUserLeagueStatsOptions({
			query: {
				login: username || "",
			},
			body: currentUserEntry || {
				rank: 0,
				score: 0,
				user: {
					id: 0,
					name: "",
					login: username || "",
					avatarUrl: "",
					htmlUrl: "",
				},
				reviewedPullRequests: [],
				numberOfReviewedPRs: 0,
				numberOfApprovals: 0,
				numberOfChangeRequests: 0,
				numberOfComments: 0,
				numberOfUnknowns: 0,
				numberOfCodeComments: 0,
			},
		}),
		enabled: Boolean(username && currentUserEntry),
	});

	// Handle team filter changes
	const handleTeamChange = (team: string) => {
		navigate({
			search: (prev) => ({
				...prev,
				team,
			}),
		});
	};

	// Handle sort changes with the correct type
	const handleSortChange = (sort: LeaderboardSortType) => {
		navigate({
			search: (prev) => ({
				...prev,
				sort,
			}),
		});
	};

	// Handle timeframe changes - note we're not passing timeframe in URL anymore
	const handleTimeframeChange = (afterDate: string, beforeDate: string) => {
		navigate({
			search: (prev) => ({
				...prev,
				after: afterDate,
				before: beforeDate,
			}),
		});
	};

	// Handle user profile navigation
	const handleUserClick = (username: string) => {
		navigate({ to: "/user/$username", params: { username } });
	};

	return (
		<LeaderboardPage
			leaderboard={leaderboardQuery.data || []}
			isLoading={leaderboardQuery.isPending || metaQuery.isPending}
			currentUser={userProfileQuery.data?.userInfo}
			currentUserEntry={currentUserEntry}
			leaguePoints={userProfileQuery.data?.userInfo?.leaguePoints}
			leaguePointsChange={leagueStatsQuery.data?.leaguePointsChange}
			teams={metaQuery.data?.teams.map((team) => team.name) || []}
			selectedTeam={team}
			selectedSort={sort}
			initialAfterDate={after}
			initialBeforeDate={before}
			leaderboardEnd={leaderboardEnd}
			leaderboardSchedule={leaderboardSchedule}
			onTeamChange={handleTeamChange}
			onSortChange={handleSortChange}
			onTimeframeChange={handleTimeframeChange}
			onUserClick={handleUserClick}
		/>
	);
}
