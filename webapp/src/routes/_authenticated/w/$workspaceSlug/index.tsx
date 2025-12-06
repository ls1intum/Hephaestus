import { useQuery } from "@tanstack/react-query";
import {
	createFileRoute,
	retainSearchParams,
	useNavigate,
} from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { endOfISOWeek, formatISO, startOfISOWeek } from "date-fns";
import { useEffect } from "react";
import { z } from "zod";
import {
	getAllTeamsOptions,
	getLeaderboardOptions,
	getUserLeagueStatsOptions,
	getUserProfileOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import { LeaderboardPage } from "@/components/leaderboard/LeaderboardPage";
import type { LeaderboardSortType } from "@/components/leaderboard/SortFilter";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";

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
	mode: z.enum(["INDIVIDUAL", "TEAM"]).default("INDIVIDUAL"),
});

// Export route with search param validation
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/")({
	component: LeaderboardContainer,
	validateSearch: zodValidator(leaderboardSearchSchema),
	// Configure search middleware to retain params when navigating
	search: {
		middlewares: [
			retainSearchParams(["team", "sort", "after", "before", "mode"]),
		],
	},
});

function LeaderboardContainer() {
	// Get the current user from auth context
	const { username } = useAuth();
	const { workspaceSlug, isLoading: isWorkspaceLoading } =
		useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);
	const showNoWorkspace = !isWorkspaceLoading && !hasWorkspace;

	// Access properly validated search params with correct types
	const { team, sort, after, before, mode } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Query for workspace details (includes schedule info)
	const workspaceQuery = useQuery({
		...getWorkspaceOptions({
			path: { workspaceSlug: slug },
		}),
		enabled: hasWorkspace,
	});

	// Query for teams in the workspace
	const teamsQuery = useQuery({
		...getAllTeamsOptions({
			path: { workspaceSlug: slug },
		}),
		enabled: hasWorkspace,
	});

	// Query for leaderboard data based on filters (API requires before; fall back when missing)
	const normalizedBefore = before
		? new Date(before)
		: new Date(endOfCurrentWeek);
	const leaderboardQuery = useQuery({
		...getLeaderboardOptions({
			path: { workspaceSlug: slug },
			query: {
				after: after ? new Date(after) : new Date(startOfCurrentWeek),
				before: normalizedBefore,
				team,
				sort,
				mode,
			},
		}),
		enabled:
			hasWorkspace && Boolean((after || startOfCurrentWeek) && teamsQuery.data),
	});

	// Query for user profile data (mirror leaderboard filters if provided)
	const userProfileOptions = getUserProfileOptions({
		path: { workspaceSlug: workspaceSlug ?? "", login: username || "" },
		query:
			after || before
				? {
					after: after ? new Date(after) : undefined,
					before: before ? new Date(before) : undefined,
				}
			: undefined,
	});

	const userProfileQuery = useQuery({
		...userProfileOptions,
		enabled: hasWorkspace && Boolean(username),
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
	const teamLabelsById = teamsList.reduce<Record<number, string>>(
		(acc, team) => {
			const label = makeLabel(team);
			acc[team.id] = label.length > 0 ? label : team.name;
			return acc;
		},
		{},
	);

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
				search: (prev) => ({
					...prev,
					team: "all",
				}),
			});
		}
	}, [team, visibleTeams, navigate]);

	useEffect(() => {
		if (mode === "TEAM" && team !== "all") {
			navigate({
				search: (prev) => ({
					...prev,
					team: "all",
				}),
			});
		}
	}, [mode, team, navigate]);

	useEffect(() => {
		if (mode === "TEAM" && sort !== "SCORE") {
			navigate({
				search: (prev) => ({
					...prev,
					sort: "SCORE" as LeaderboardSortType,
				}),
			});
		}
	}, [mode, sort, navigate]);

	// Get the leaderboard schedule from workspace config
	const scheduledTime = workspaceQuery.data?.leaderboardScheduleTime || "9:00";
	const scheduledDay = workspaceQuery.data?.leaderboardScheduleDay ?? 2;
	const [hours, minutes] = scheduledTime
		.split(":")
		.map((part: string) => Number.parseInt(part, 10));
	const leaderboardSchedule = {
		day: scheduledDay,
		hour: hours || 9,
		minute: minutes || 0,
	};

	// Calculate leaderboard end date with the correct time
	const endDate = new Date(before);

	// Adjust the end date to include the schedule time from server metadata
	endDate.setHours(leaderboardSchedule.hour, leaderboardSchedule.minute, 0, 0);

	const leaderboardEnd = formatISO(endDate);

	// Query for league points change data if we have a current user entry
	const leagueStatsQuery = useQuery({
		...getUserLeagueStatsOptions({
			path: { workspaceSlug: slug },
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
		enabled: hasWorkspace && Boolean(username && currentUserEntry),
	});

	if (showNoWorkspace) {
		return <NoWorkspace />;
	}

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
	const handleTimeframeChange = (afterDate: string, beforeDate?: string) => {
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
		if (!hasWorkspace) return;
		navigate({
			to: "/w/$workspaceSlug/user/$username",
			params: { workspaceSlug: slug, username },
		});
	};

	const handleModeChange = (newMode: "INDIVIDUAL" | "TEAM") => {
		navigate({
			search: (prev) => ({
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
			search: (prev) => ({
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
			isLoading={
				isWorkspaceLoading || leaderboardQuery.isPending || teamsQuery.isPending
			}
			currentUser={userProfileQuery.data?.userInfo}
			currentUserEntry={currentUserEntry}
			leaguePoints={userProfileQuery.data?.userInfo?.leaguePoints}
			leaguePointsChange={leagueStatsQuery.data?.leaguePointsChange}
			teamOptions={teamOptions}
			teamLabelsById={teamLabelsById}
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
			selectedMode={mode}
			onModeChange={handleModeChange}
			onTeamClick={handleTeamClick}
		/>
	);
}
