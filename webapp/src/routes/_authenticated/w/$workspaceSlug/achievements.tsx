import { createFileRoute } from "@tanstack/react-router";
import { AchievementsView } from "@/components/achievements/AchievementsView";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/achievements")({
	component: AchievementsPage,
});

/**
 * Shortcut route for viewing the current user's own achievements.
 * Delegates to the shared AchievementsView component.
 */
function AchievementsPage() {
	const { userProfile, getUserProfilePictureUrl, username } = useAuth();
	const selectedSlug = useWorkspaceStore((state) => state.selectedSlug);

	return (
		<AchievementsView
			workspaceSlug={selectedSlug || ""}
			targetUsername={username || ""}
			isOwnProfile={true}
			fallbackName={userProfile?.name || userProfile?.username}
			fallbackAvatarUrl={getUserProfilePictureUrl()}
		/>
	);
}
