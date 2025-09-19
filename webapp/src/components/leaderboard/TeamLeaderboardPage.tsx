import { LeaderboardLegend } from "./LeaderboardLegend.tsx";
import { LeaderboardFilter } from "./LeaderboardFilter.tsx";
import type { LeaderboardSortType } from "./SortFilter.tsx";
import { TeamLeaderboardTable } from "./TeamLeaderboardTable.tsx";
import type {TeamInfo, TeamLeaderboardEntry} from "@/api/types.gen.ts";

interface TeamLeaderboardPageProps {
    teamLeaderboard?: TeamLeaderboardEntry[];
    isLoading: boolean;
    currentTeam?: TeamInfo;
    onTeamClick?: (teamName: string) => void;

    teams: string[];
    onTeamChange?: (teams: string) => void;
    onSortChange?: (sort: LeaderboardSortType) => void;
    onTimeframeChange?: (
        afterDate: string,
        beforeDate: string,
        timeframe?: string,
    ) => void;
    selectedTeam?: string;
    selectedSort?: LeaderboardSortType;
    initialAfterDate?: string;
    initialBeforeDate?: string;
    leaderboardSchedule?: {
		day: number;
		hour: number;
		minute: number;
	};
}

export function TeamLeaderboardPage({
    isLoading,
    teamLeaderboard,
    currentTeam,
    onTeamClick,

    teams,
    onTeamChange,
    onSortChange,
    onTimeframeChange,
    selectedTeam,
    selectedSort,
    initialAfterDate,
    initialBeforeDate,
    leaderboardSchedule,
}: TeamLeaderboardPageProps) {

    const formattedSchedule = leaderboardSchedule
		? {
				...leaderboardSchedule,
				formatted: `${String(leaderboardSchedule.hour).padStart(2, "0")}:${String(leaderboardSchedule.minute).padStart(2, "0")} on day ${leaderboardSchedule.day}`,
			}
		: undefined;

    return (
        <div className="flex flex-col items-center">
            <div className="w-full">
                <h1 className="text-3xl font-bold mb-4">
                    Team Leaderboard
                </h1>
                <div className="grid grid-cols-1 xl:grid-cols-4 gap-y-4 xl:gap-4">

                    <div className="space-y-4 col-span-1">
                        <h2 className="text-cyan-500">
                            TODO: Work in progress filter - ist nur schon mal hier für das Layout und so zum Testen
                        </h2>
                        <div className="xl:sticky xl:top-4 xl:self-start xl:max-h-[calc(100vh-2rem)] xl:overflow-auto">
                            {/* TODO: review if TeamLeaderboardFiter is necessary or if LeaderboardFilter can be adapted accordingly */}
                            <LeaderboardFilter
                                teams={teams}
                                onTeamChange={onTeamChange}
                                onSortChange={onSortChange}
                                onTimeframeChange={onTimeframeChange}
                                selectedTeam={selectedTeam}
                                selectedSort={selectedSort}
                                initialAfterDate={initialAfterDate}
                                initialBeforeDate={initialBeforeDate}
                                leaderboardSchedule={formattedSchedule}
                            />
                        </div>
                    </div>

                    <div className="col-span-2 space-y-4">
                        <div>
                            <TeamLeaderboardTable
                                isLoading={isLoading}
                                teamLeaderboard={teamLeaderboard}
                                currentTeam={currentTeam}
                                onTeamClick={onTeamClick}
                            />
                        </div>
                    </div>

                    <div className="col-span-1 xl:sticky xl:top-4 xl:self-start xl:max-h-[calc(100vh-2rem)] xl:overflow-auto">
                        <h2 className="text-cyan-500">
                            TODO: Rework this to show team leaderboard formulas instead of normal leaderboard legend
                        </h2>
                        <LeaderboardLegend />
                    </div>

                </div>
            </div>
        </div>
    );
}

