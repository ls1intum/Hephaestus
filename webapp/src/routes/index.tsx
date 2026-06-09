import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { LandingPage } from "@/components/info/landing/LandingPage";
import { RedirectToWorkspace } from "@/components/workspace/RedirectToWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";
import { resolveCurrentUser } from "@/integrations/auth/guard";

/**
 * Public home route. Signed-out visitors see the marketing landing page; signed-in visitors are
 * routed straight to their workspace. The session is resolved in `beforeLoad` (shared query cache),
 * so the first paint is already correct — neither the landing page nor the app chrome flashes for
 * the wrong audience.
 */
export const Route = createFileRoute("/")({
	beforeLoad: async ({ context }) => {
		await resolveCurrentUser(context.queryClient);
	},
	component: IndexPage,
});

function IndexPage() {
	const { isAuthenticated } = useAuth();
	return isAuthenticated ? <RedirectToWorkspace /> : <LandingContainer />;
}

function LandingContainer() {
	const { login, isAuthenticated } = useAuth();
	const { workspaceSlug, workspaces } = useActiveWorkspaceSlug();
	const navigate = useNavigate();

	const handleGoToDashboard = () => {
		const targetSlug = workspaceSlug ?? workspaces[0]?.workspaceSlug;
		if (targetSlug) {
			navigate({ to: "/w/$workspaceSlug", params: { workspaceSlug: targetSlug } });
		}
	};

	return (
		// Cancel the `p-4` the root layout applies to <main> so the landing sections bleed full-width.
		<div className="-m-4">
			<LandingPage
				onSignIn={(idpHint) => login(idpHint, "/")}
				onGoToDashboard={handleGoToDashboard}
				isSignedIn={isAuthenticated}
			/>
		</div>
	);
}
