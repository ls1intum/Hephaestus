import { createFileRoute } from "@tanstack/react-router";
import { AchievementsView } from "@/components/achievements/AchievementsView";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/achievements",
)({
	component: UserAchievementsPage,
});

/**
 * Parameterized route for viewing any user's achievements.
 * Renders the same skill tree / list view but with the target user's data.
 */
function UserAchievementsPage() {
	const { workspaceSlug, username } = Route.useParams();
	const { isCurrentUser } = useAuth();

	return (
		<AchievementsView
			workspaceSlug={workspaceSlug}
			targetUsername={username}
			isOwnProfile={isCurrentUser(username)}
		/>
	);
}
