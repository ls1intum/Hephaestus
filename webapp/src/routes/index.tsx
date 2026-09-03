import { createFileRoute, redirect, useNavigate } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { StandardPageSurface } from "@/components/core/StandardPageSurface";
import { LandingPage } from "@/components/info/landing/LandingPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";
import { resolveCurrentUser } from "@/integrations/auth/guard";

/**
 * Public home route. Signed-out visitors see the marketing landing page; signed-in visitors are
 * routed straight to their workspace. Both the session and the workspace list are resolved in
 * `beforeLoad` (shared query cache), so the first paint is already correct — neither the landing
 * page nor the app chrome flashes for the wrong audience, and a member of workspaces never paints
 * the empty state on the way to one.
 */
export const Route = createFileRoute("/")({
	staticData: { surface: "bleed" },
	beforeLoad: async ({ context }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (!user) return;
		const workspaces = await context.queryClient
			.query(listWorkspacesOptions())
			.catch(() => undefined);
		const workspaceSlug = workspaces?.[0]?.workspaceSlug;
		if (workspaceSlug) {
			throw redirect({ to: "/w/$workspaceSlug", params: { workspaceSlug }, replace: true });
		}
	},
	component: IndexPage,
});

function IndexPage() {
	const { isAuthenticated } = useAuth();
	return isAuthenticated ? (
		<StandardPageSurface className="h-full">
			<NoWorkspace />
		</StandardPageSurface>
	) : (
		<LandingContainer />
	);
}

function LandingContainer() {
	const { login, isAuthenticated } = useAuth();
	const { chromeWorkspaceSlug } = useActiveWorkspaceSlug();
	const navigate = useNavigate();

	const handleGoToDashboard = () => {
		if (chromeWorkspaceSlug) {
			void navigate({ to: "/w/$workspaceSlug", params: { workspaceSlug: chromeWorkspaceSlug } });
		}
	};

	return (
		<LandingPage
			onSignIn={(idpHint) => login(idpHint, "/")}
			onGoToDashboard={handleGoToDashboard}
			isSignedIn={isAuthenticated}
		/>
	);
}
