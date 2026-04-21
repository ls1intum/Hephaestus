import { createFileRoute, Navigate } from "@tanstack/react-router";
import { AchievementsView } from "@/components/achievements/AchievementsView";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/achievements")({
	component: AchievementsPage,
});

/**
 * Shortcut route for viewing the current user's own achievements.
 * Delegates to the shared AchievementsView component.
 */
function AchievementsPage() {
	const { userProfile, getUserProfilePictureUrl, username } = useAuth();
	const { workspaceSlug } = Route.useParams();
	const { achievementsEnabled, isLoading } = useWorkspaceFeatures(workspaceSlug);

	if (!isLoading && !achievementsEnabled && workspaceSlug && username) {
		return (
			<Navigate
				to="/w/$workspaceSlug/user/$username"
				params={{ workspaceSlug, username }}
				replace
			/>
		);
	}

	if (isLoading || !achievementsEnabled) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	return (
		<AchievementsView
			workspaceSlug={workspaceSlug}
			targetUsername={username || ""}
			isOwnProfile={true}
			fallbackName={userProfile?.name || userProfile?.username}
			fallbackAvatarUrl={getUserProfilePictureUrl()}
		/>
	);
}
