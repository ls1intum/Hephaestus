import { useQuery } from "@tanstack/react-query";
import {
	createFileRoute,
	Link,
	Navigate,
	retainSearchParams,
	useNavigate,
} from "@tanstack/react-router";
import { formatISO } from "date-fns";
import { useEffect } from "react";
import { z } from "zod";
import {
	computeUserLeagueStatsOptions,
	getAllTeamsOptions,
	getLeaderboardOptions,
	getUserProfileOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { LeaderboardPage } from "@/components/leaderboard/LeaderboardPage";
import type { LeaderboardSortType } from "@/components/leaderboard/SortFilter";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";
import {
	DEFAULT_SCHEDULE,
	formatDateRangeForApi,
	getLeaderboardWeekEnd,
	getLeaderboardWeekStart,
	type LeaderboardSchedule,
} from "@/lib/timeframe";

const leaderboardSearchSchema = z.object({
	team: z.string().default("all"),
	sort: z.enum(["SCORE", "LEAGUE_POINTS"]).default("SCORE"),
	after: z.string().optional(),
	before: z.string().optional(),
	mode: z.enum(["INDIVIDUAL", "TEAM"]).default("INDIVIDUAL"),
});

type LeaderboardSearchParams = z.infer<typeof leaderboardSearchSchema>;

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/")({
	component: LeaderboardContainer,
	validateSearch: leaderboardSearchSchema,
	search: {
		middlewares: [retainSearchParams(["team", "sort", "after", "before", "mode"])],
	},
});

function LeaderboardContainer() {
	const { username } = useAuth();
	const { workspaceSlug, providerType, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const featureState = useWorkspaceFeatures(workspaceSlug);
	const leaderboardEnabled = featureState.features?.leaderboardEnabled;
	const leaguesEnabled = featureState.features?.leaguesEnabled;
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);
	const showNoWorkspace = !isWorkspaceLoading && !hasWorkspace;

	const { team, sort, after, before, mode } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const workspaceQuery = useQuery({
		...getWorkspaceOptions({
			path: { workspaceSlug: slug },
		}),
		enabled: hasWorkspace,
	});

	const getSchedule = (): LeaderboardSchedule => {
		if (!workspaceQuery.data) return DEFAULT_SCHEDULE;

		const scheduledTime = workspaceQuery.data.leaderboardScheduleTime || "9:00";
		const scheduledDay = workspaceQuery.data.leaderboardScheduleDay ?? 2;
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

	const getEffectiveDates = () => {
		if (after) {
			return { after, before };
		}
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

	const teamsQuery = useQuery({
		...getAllTeamsOptions({
			path: { workspaceSlug: slug },
		}),
		enabled: hasWorkspace,
	});

	const leaderboardQuery = useQuery({
		...getLeaderboardOptions({
			path: { workspaceSlug: slug },
			query: {
				after: parsedAfter ?? new Date(),
				before: parsedBefore ?? new Date(),
				team,
				sort,
				mode,
			},
		}),
		placeholderData: (previousData) => previousData,
		enabled: hasWorkspace && Boolean(parsedAfter && teamsQuery.data),
	});

	const userProfileOptions = getUserProfileOptions({
		path: { workspaceSlug: workspaceSlug ?? "", login: username || "" },
		query: {
			after: parsedAfter,
			before: parsedBefore,
		},
	});

	const userProfileQuery = useQuery({
		...userProfileOptions,
		placeholderData: (previousData) => previousData,
		enabled: hasWorkspace && Boolean(username),
	});
	const currentUserEntry = username
		? leaderboardQuery.data?.find(
				(entry) => entry.user?.login?.toLowerCase() === username.toLowerCase(),
			)
		: undefined;

	type MetaTeam = {
		id: number;
		name: string;
		parentId?: number;
		hidden?: boolean;
	};

	const teamsList = (teamsQuery.data ?? []) as MetaTeam[];
	const teamById = new Map<number, MetaTeam>(teamsList.map((t) => [t.id, t]));

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

	const endDate = parsedBefore ? new Date(parsedBefore) : new Date();

	endDate.setHours(schedule.hour, schedule.minute, 0, 0);

	const leaderboardEnd = formatISO(endDate);

	const leagueStatsQuery = useQuery({
		...computeUserLeagueStatsOptions({
			path: { workspaceSlug: slug, login: username || "" },
			query: {
				after: parsedAfter ?? new Date(),
				before: parsedBefore ?? new Date(),
			},
		}),
		enabled: hasWorkspace && Boolean(username) && Boolean(parsedAfter) && Boolean(parsedBefore),
	});

	if (
		!featureState.isLoading &&
		!featureState.isError &&
		leaderboardEnabled === false &&
		workspaceSlug &&
		username
	) {
		return (
			<Navigate
				to="/w/$workspaceSlug/user/$username"
				params={{ workspaceSlug, username }}
				replace
			/>
		);
	}

	if (showNoWorkspace) {
		return <NoWorkspace />;
	}

	if (featureState.isError) {
		return (
			<QueryErrorAlert
				error={featureState.error}
				title="Couldn't load workspace features"
				onRetry={featureState.refetch}
			/>
		);
	}

	if (featureState.isLoading || leaderboardEnabled !== true) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	const handleTeamChange = (team: string) => {
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				team,
			}),
		});
	};

	const handleSortChange = (sort: LeaderboardSortType) => {
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				sort,
			}),
		});
	};

	const handleTimeframeChange = (afterDate: string, beforeDate?: string) => {
		navigate({
			search: (prev: LeaderboardSearchParams) => ({
				...prev,
				after: afterDate,
				before: beforeDate,
			}),
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

	return (
		<LeaderboardPage
			providerType={providerType}
			leaderboard={leaderboardQuery.data || []}
			isLoading={
				isWorkspaceLoading ||
				teamsQuery.isPending ||
				(leaderboardQuery.isPending && !leaderboardQuery.data)
			}
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
			renderUserLink={(username, children) => (
				<Link
					to="/w/$workspaceSlug/user/$username"
					params={{ workspaceSlug: slug, username }}
					className="inline-flex rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
				>
					{children}
				</Link>
			)}
			selectedMode={mode}
			onModeChange={handleModeChange}
			renderTeamLink={(teamId, children) => {
				const label = teamLabelsById[teamId];
				return label ? (
					<Link
						to="."
						search={(previous) => ({
							...previous,
							mode: "INDIVIDUAL",
							sort: "SCORE",
							team: label,
						})}
						className="inline-flex rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
					>
						{children}
					</Link>
				) : (
					children
				);
			}}
			leaguesEnabled={leaguesEnabled === true}
		/>
	);
}
