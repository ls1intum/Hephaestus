import type { LeaderboardEntry, TeamInfo } from "@/api/types.gen";
import { LeaderboardLegend } from "../leaderboard/LeaderboardLegend"; 

export function TeamLeaderboardPage() {

    return (
        <div className="flex flex-col items-center">
            <div className="w-full">
                <h1 className="text-3xl font-bold mb-4">
                    Team Leaderboard
                </h1>
                <div className="grid grid-cols-1 xl:grid-cols-4 gap-y-4 xl:gap-4">

                    <div className="space-y-4 col-span-1">
                        <h2>Here is the team leaderboard options tab (but in its own container with its own styling)</h2>
                    </div>

                    <div className="col-span-2 space-y-4">
                        <h2>Here is the space for the main table on this page where you can see the different teams</h2>
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

