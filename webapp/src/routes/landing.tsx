import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { LandingPage } from "@/components/info/landing/LandingPage";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute("/landing")({
	component: LandingContainer,
});

export function LandingContainer() {
	const { login, isAuthenticated } = useAuth();
	const { workspaceSlug, workspaces } = useActiveWorkspaceSlug();
	const navigate = useNavigate();

	const handleGoToDashboard = () => {
		const targetSlug = workspaceSlug ?? workspaces[0]?.workspaceSlug;
		if (targetSlug) {
			navigate({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug: targetSlug },
			});
		}
	};

	return (
		<div className="-m-4">
			<LandingPage
				onSignIn={() => login()}
				onGoToDashboard={handleGoToDashboard}
				isSignedIn={isAuthenticated}
			/>
		</div>
	);
}
