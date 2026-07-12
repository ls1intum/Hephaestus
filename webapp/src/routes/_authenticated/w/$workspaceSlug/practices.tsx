import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { MyPracticesPage } from "@/components/practices/MyPracticesPage";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/practices")({
	component: PracticesRoute,
});

/**
 * The developer self view: the caller's own practice report. Any signed-in member; the server
 * scopes the data to the caller. Hidden (with a silent redirect) when the workspace has the
 * practices feature off, mirroring the achievements route.
 */
function PracticesRoute() {
	const { workspaceSlug } = Route.useParams();
	const { username } = useAuth();
	const navigate = useNavigate();
	const { practicesEnabled, isLoading } = useWorkspaceFeatures();

	useEffect(() => {
		if (!isLoading && !practicesEnabled && workspaceSlug && username) {
			// Silent redirect — the nav entry is already hidden when the feature is off.
			navigate({
				to: "/w/$workspaceSlug/user/$username",
				params: { workspaceSlug, username },
				replace: true,
			});
		}
	}, [isLoading, practicesEnabled, workspaceSlug, username, navigate]);

	if (isLoading || !practicesEnabled) {
		return (
			<div className="flex h-96 items-center justify-center">
				<Spinner className="size-8" />
			</div>
		);
	}

	return <MyPracticesPage workspaceSlug={workspaceSlug} />;
}
