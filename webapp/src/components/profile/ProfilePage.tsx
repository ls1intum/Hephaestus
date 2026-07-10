import { XCircleIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { Profile, ProfileActivityMonitor } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import type { ActivityMonitorFilters } from "@/lib/activity-monitor";
import type { ProviderType } from "@/lib/provider";
import type { ReviewCycleSchedule } from "@/lib/timeframe";
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
	/** Review-cycle schedule for proper week calculations */
	schedule?: ReviewCycleSchedule;
	achievementsEnabled?: boolean;
	/**
	 * Optional private practice-reflection section rendered between the header and the activity
	 * monitor. Only the self-view (workspace home) fills this — it is fed exclusively by the
	 * server-gated `GET /practices/reports/me`, so another developer's cards can never appear here.
	 */
	practicesSlot?: ReactNode;
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
	practicesSlot,
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
		<div className="pt-4 flex flex-col gap-8">
			<ProfileHeader
				user={profileData?.userInfo}
				isLoading={isLoading}
				workspaceSlug={workspaceSlug}
				achievementsEnabled={achievementsEnabled}
			/>
			{practicesSlot && (
				<section aria-label="My practices" className="mx-8">
					{practicesSlot}
				</section>
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
