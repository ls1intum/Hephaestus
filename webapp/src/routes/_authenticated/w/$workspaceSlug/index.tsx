import { useQuery } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { formatISO } from "date-fns";
import { useEffect } from "react";
import { z } from "zod";
import {
	getAllTeamsOptions,
	getLeaderboardOptions,
	getUserLeagueStatsOptions,
	getUserProfileOptions,
} from "@/api/@tanstack/react-query.gen";
import { LeaderboardPage } from "@/components/leaderboard/LeaderboardPage";
import type { LeaderboardSortType } from "@/components/leaderboard/SortFilter";
import { useWorkspace } from "@/hooks/use-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";
import {
	formatDateRangeForApi,
	getLeaderboardWeekEnd,
	getLeaderboardWeekStart,
	type LeaderboardSchedule,
} from "@/lib/timeframe";

// Define search params schema for validation and types
// Defaults are computed dynamically using the leaderboard schedule from workspace
const leaderboardSearchSchema = z.object({
	team: z.string().default("all"),
	sort: z.enum(["SCORE", "LEAGUE_POINTS"]).default("SCORE"),
	after: z.string().optional(),
	before: z.string().optional(),
	mode: z.enum(["INDIVIDUAL", "TEAM"]).default("INDIVIDUAL"),
});

type LeaderboardSearchParams = z.infer<typeof leaderboardSearchSchema>;

// Export route with search param validation
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/")({
	component: LeaderboardContainer,
	validateSearch: zodValidator(leaderboardSearchSchema),
	// Configure search middleware to retain params when navigating
	search: {
		middlewares: [retainSearchParams(["team", "sort", "after", "before", "mode"])],
	},
});

function LeaderboardContainer() {
	// Get the current user from auth context
	const { username } = useAuth();
	// Workspace is loaded by the parent layout route and provided via context
	const workspace = useWorkspace();
	const workspaceSlug = workspace.workspaceSlug;

	// Access properly validated search params with correct types
	const { team, sort, after, before, mode } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Extract leaderboard schedule from workspace config
	const getSchedule = (): LeaderboardSchedule => {
		const scheduledTime = workspace.leaderboardScheduleTime || "9:00";
		const scheduledDay = workspace.leaderboardScheduleDay ?? 2;
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
	// This ensures dates passed to TimeframeFilter align with getLeaderboardWeekStart
	const getEffectiveDates = () => {
		if (after) {
			return { after, before };
		}
		// Default to "this week" using the leaderboard schedule (bounded for API)
		const now = new Date();
		const weekStart = getLeaderboardWeekStart(now, schedule);
		const weekEnd = getLeaderboardWeekEnd(weekStart);
		return formatDateRangeForApi({ after: weekStart, before: weekEnd });
	};
	const effectiveDates = getEffectiveDates();

	const parseDateParam = (value?: string | null) => {
		if (!value) return undefined;
		const parsed = new Date(value);
		return Number.isNaN(parsed.getTime()) ? undefined : parsed;
	};

	const parsedAfter = parseDateParam(effectiveDates.after);
	const parsedBefore = parseDateParam(effectiveDates.before);

	// Query for teams in the workspace
	const teamsQuery = useQuery({
		...getAllTeamsOptions({
			path: { workspaceSlug },
		}),
	});

	// Query for leaderboard data based on filters
	const leaderboardQuery = useQuery({
		...getLeaderboardOptions({
			path: { workspaceSlug },
			query: {
				after: parsedAfter ?? new Date(),
				before: parsedBefore ?? new Date(),
				team,
				sort,
				mode,
			},
		}),
		placeholderData: (previousData) => previousData,
		enabled: Boolean(parsedAfter && teamsQuery.data),
	});

	// Query for user profile data (mirror leaderboard filters if provided)
	const userProfileOptions = getUserProfileOptions({
		path: { workspaceSlug, login: username || "" },
		query: {
			after: parsedAfter,
			before: parsedBefore,
		},
	});

	const userProfileQuery = useQuery({
		...userProfileOptions,
		placeholderData: (previousData) => previousData,
		enabled: Boolean(username),
	});
	// Find the current user's entry in the leaderboard
	const currentUserEntry = username
		? leaderboardQuery.data?.find(
				(entry) => entry.user?.login?.toLowerCase() === username.toLowerCase(),
			)
		: undefined;

	// Compute visible teams list (exclude hidden teams)
	type MetaTeam = {
		id: number;
		name: string;
		parentId?: number;
		hidden?: boolean;
	};

	// Build a map for id->team to compute visible-only path labels
	const teamsList = (teamsQuery.data ?? []) as MetaTeam[];
	const teamById = new Map<number, MetaTeam>(teamsList.map((t) => [t.id, t]));

	// Helper to create the visible-only path label for a team
	const makeLabel = (t: MetaTeam): string => {
		const names: string[] = [];
		let cur: MetaTeam | undefined = t;
		while (cur) {
			if (!cur.hidden) names.push(cur.name);
			const parent: MetaTeam | undefined =
				cur.parentId !== undefined ? teamById.get(cur.parentId) : undefined;
			cur = parent;
		}
		return names.reverse().join(" / ");
	};

	// Valid selectable values are the full visible paths
	const teamLabelsById = teamsList.reduce<Record<number, string>>((acc, team) => {
		const label = makeLabel(team);
		acc[team.id] = label.length > 0 ? label : team.name;
		return acc;
	}, {});

	const visibleTeamEntries = teamsList
		.filter((t) => !t.hidden)
		.map((team) => ({ team, label: teamLabelsById[team.id] }));

	const visibleTeams = visibleTeamEntries.map((entry) => entry.label);

	const teamOptions = visibleTeamEntries
		.map(({ label }) => ({ value: label, label }))
		.sort((a, b) => a.label.localeCompare(b.label));

	// If current selected team is hidden (or no longer present), reset to 'all'
	useEffect(() => {
		if (team && team !== "all" && !visibleTeams.includes(team)) {
			navigate({
				search: (prev: LeaderboardSearchParams) => ({
					...prev,
					team: "all",
				}),
			});
		}
	}, [team, visibleTeams, navigate]);

	useEffect(() => {
		if (mode === "TEAM" && team !== "all") {
			navigate({
				search: (prev: LeaderboardSearchParams) => ({
					...prev,
					team: "all",
				}),
			});
		}
	}, [mode, team, navigate]);

	useEffect(() => {
		if (mode === "TEAM" && sort !== "SCORE") {
			navigate({
				search: (prev: LeaderboardSearchParams) => ({
					...prev,
					sort: "SCORE" as LeaderboardSortType,
				}),
			});
		}
	}, [mode, sort, navigate]);

	// Calculate leaderboard end date with the correct time
	const endDate = parsedBefore ? new Date(parsedBefore) : new Date();

	// Adjust the end date to include the schedule time from server metadata
	endDate.setHours(schedule.hour, schedule.minute, 0, 0);

	const leaderboardEnd = formatISO(endDate);

	// Query for league points change data if we have a current user entry
	const leagueStatsQuery = useQuery({
		...getUserLeagueStatsOptions({
			path: { workspaceSlug },
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
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				team,
			}),
		});
	};

	// Handle sort changes with the correct type
	const handleSortChange = (sort: LeaderboardSortType) => {
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				sort,
			}),
		});
	};

	// Handle timeframe changes - note we're not passing timeframe in URL anymore
	const handleTimeframeChange = (afterDate: string, beforeDate?: string) => {
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				after: afterDate,
				before: beforeDate,
			}),
		});
	};

	// Handle user profile navigation
	const handleUserClick = (username: string) => {
		navigate({
			to: "/w/$workspaceSlug/user/$username",
			params: { workspaceSlug, username },
		});
	};

	const handleModeChange = (newMode: "INDIVIDUAL" | "TEAM") => {
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				mode: newMode,
				team: newMode === "TEAM" ? "all" : prev.team,
				sort: newMode === "TEAM" ? "SCORE" : prev.sort,
			}),
		});
	};

	const handleTeamClick = (teamId: number) => {
		const label = teamLabelsById[teamId];
		if (!label) return;
		// Expand the team path and navigate to INDIVIDUAL mode with that team filter
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				mode: "INDIVIDUAL",
				sort: "SCORE",
				team: label,
			}),
		});
	};

	return (
		<LeaderboardPage
			leaderboard={leaderboardQuery.data || []}
			isLoading={teamsQuery.isPending || (leaderboardQuery.isPending && !leaderboardQuery.data)}
			currentUser={userProfileQuery.data?.userInfo}
			currentUserEntry={currentUserEntry}
			leaguePoints={userProfileQuery.data?.userInfo?.leaguePoints}
			leaguePointsChange={leagueStatsQuery.data?.leaguePointsChange}
			teamOptions={teamOptions}
			teamLabelsById={teamLabelsById}
			selectedTeam={team}
			selectedSort={sort}
			initialAfterDate={effectiveDates.after}
			initialBeforeDate={effectiveDates.before}
			leaderboardEnd={leaderboardEnd}
			leaderboardSchedule={schedule}
			onTeamChange={handleTeamChange}
			onSortChange={handleSortChange}
			onTimeframeChange={handleTimeframeChange}
			onUserClick={handleUserClick}
			selectedMode={mode}
			onModeChange={handleModeChange}
			onTeamClick={handleTeamClick}
		/>
	);
}
