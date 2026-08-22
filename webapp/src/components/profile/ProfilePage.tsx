import { XCircleIcon } from "lucide-react";
import type { Profile, ProfileActivityMonitor } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Separator } from "@/components/ui/separator";
import type { ActivityMonitorFilters } from "@/lib/activity-monitor";
import type { ProviderType } from "@/lib/provider";
import type { LeaderboardSchedule } from "@/lib/timeframe";
import {
	PracticeAreaStatusCard,
	type PracticeAreaStatusSectionProps,
} from "./PracticeAreaStatusCard";
import { ProfileContent } from "./ProfileContent";
import { ProfileHeader } from "./ProfileHeader";

interface ProfileProps {
	providerType?: ProviderType;
	profileData?: Profile;
	activityMonitorData?: ProfileActivityMonitor;
	activityMonitorFilters: ActivityMonitorFilters;
	onActivityMonitorFiltersChange: (filters: ActivityMonitorFilters) => void;
	isLoading: boolean;
	error: boolean;
	username: string;
	currUserIsDashboardUser: boolean;
	workspaceSlug: string;
	after?: string;
	before?: string;
	onTimeframeChange?: (afterDate: string, beforeDate?: string) => void;
	/** Leaderboard schedule for proper week calculations */
	schedule?: LeaderboardSchedule;
	achievementsEnabled?: boolean;
	progressionEnabled?: boolean;
	leaguesEnabled?: boolean;
	/** Own-profile practice-area status section; omit to hide (e.g. on someone else's profile). */
	practiceAreaStatus?: PracticeAreaStatusSectionProps;
}

export function ProfilePage({
	providerType = "GITHUB",
	profileData,
	activityMonitorData,
	activityMonitorFilters,
	onActivityMonitorFiltersChange,
	isLoading,
	error,
	username,
	currUserIsDashboardUser,
	workspaceSlug,
	after,
	before,
	onTimeframeChange,
	schedule,
	achievementsEnabled = true,
	progressionEnabled = true,
	leaguesEnabled = true,
	practiceAreaStatus,
}: ProfileProps) {
	if (error) {
		return (
			<div className="flex items-center justify-center gap-2">
				<Alert variant="destructive" className="max-w-xl">
					<XCircleIcon className="h-4 w-4" />
					<AlertTitle>Something went wrong...</AlertTitle>
					<AlertDescription>User couldn't be loaded. Please try again later.</AlertDescription>
				</Alert>
			</div>
		);
	}

	return (
		<div className="mx-auto flex w-full max-w-6xl flex-col gap-8">
			<ProfileHeader
				user={profileData?.userInfo}
				firstContribution={profileData?.firstContribution}
				contributedRepositories={profileData?.contributedRepositories}
				leaguePoints={profileData?.userInfo?.leaguePoints}
				userXpRecord={profileData?.xpRecord}
				isLoading={isLoading}
				workspaceSlug={workspaceSlug}
				achievementsEnabled={achievementsEnabled}
				progressionEnabled={progressionEnabled}
				leaguesEnabled={leaguesEnabled}
			/>
			{practiceAreaStatus && (
				<>
					<PracticeAreaStatusCard {...practiceAreaStatus} />
					{/* Same section rule the area detail page uses, so the practice surfaces read as one
					    family and the activity monitor is visibly a separate section. */}
					<Separator />
				</>
			)}
			<ProfileContent
				providerType={providerType}
				activityMonitorData={activityMonitorData}
				activityMonitorFilters={activityMonitorFilters}
				onActivityMonitorFiltersChange={onActivityMonitorFiltersChange}
				isLoading={isLoading}
				username={username}
				displayName={profileData?.userInfo?.name}
				currUserIsDashboardUser={currUserIsDashboardUser}
				workspaceSlug={workspaceSlug}
				afterDate={after}
				beforeDate={before}
				onTimeframeChange={onTimeframeChange}
				schedule={schedule}
			/>
		</div>
	);
}
