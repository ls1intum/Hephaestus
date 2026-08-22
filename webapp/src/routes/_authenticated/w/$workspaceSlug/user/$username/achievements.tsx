import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { AchievementsView } from "@/components/achievements/AchievementsView";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
	const featureState = useWorkspaceFeatures(workspaceSlug);
	const achievementsEnabled = featureState.features?.achievementsEnabled;

	useEffect(() => {
		if (!featureState.isLoading && !featureState.isError && achievementsEnabled === false) {
			void navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [
		featureState.isLoading,
		featureState.isError,
		achievementsEnabled,
		workspaceSlug,
		username,
		navigate,
	]);

	if (featureState.isError) {
		return (
			<QueryErrorAlert
				error={featureState.error}
				title="Couldn't load workspace features"
				onRetry={featureState.refetch}
			/>
		);
	}

	if (featureState.isLoading || achievementsEnabled !== true) {
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
