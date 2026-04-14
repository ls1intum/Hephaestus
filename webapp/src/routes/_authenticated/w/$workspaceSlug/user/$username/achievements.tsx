import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { AchievementsView } from "@/components/achievements/AchievementsView";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/achievements",
)({
	component: UserAchievementsPage,
	staticData: {
		workspaceSwitch: { target: "workspace.home" },
	},
});

/**
 * Parameterized route for viewing any user's achievements.
 * Renders the same skill tree / list view but with the target user's data.
 */
function UserAchievementsPage() {
	const { workspaceSlug, username } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const navigate = useNavigate();
	const { achievementsEnabled, isLoading } = useWorkspaceFeatures(workspaceSlug);

	useEffect(() => {
		if (!isLoading && !achievementsEnabled && workspaceSlug && username) {
			// Silent redirect — UI elements are already hidden when disabled
			navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [isLoading, achievementsEnabled, workspaceSlug, username, navigate]);

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
			targetUsername={username}
			isOwnProfile={isCurrentUser(username)}
		/>
	);
}
