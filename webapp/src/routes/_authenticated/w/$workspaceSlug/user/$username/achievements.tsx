import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { AchievementsView } from "@/components/achievements/AchievementsView";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/achievements",
)({
	staticData: { surface: "fullscreen" },
	component: UserAchievementsPage,
});

function UserAchievementsPage() {
	const { workspaceSlug, username } = Route.useParams();
	const { isCurrentUser } = useAuth();
	const navigate = useNavigate();
	const { achievementsEnabled, isLoading } = useWorkspaceFeatures(workspaceSlug);

	useEffect(() => {
		if (!isLoading && !achievementsEnabled && workspaceSlug && username) {
			navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [isLoading, achievementsEnabled, workspaceSlug, username, navigate]);

	if (isLoading || !achievementsEnabled) {
		return (
			<div className="flex min-h-0 flex-1 items-center justify-center">
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
