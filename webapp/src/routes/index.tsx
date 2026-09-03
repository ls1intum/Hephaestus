import { createFileRoute, redirect } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { StandardPageSurface } from "@/components/core/StandardPageSurface";
import { LandingPage } from "@/components/info/landing/LandingPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
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
		// A list that cannot be fetched is not an empty one: let the error surface rather than tell a
		// member of workspaces that they are in none.
		const workspaces = await context.queryClient.query(listWorkspacesOptions());
		const workspaceSlug = workspaces[0]?.workspaceSlug;
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

/** Only reached signed out, so the landing page needs no signed-in affordance. */
function LandingContainer() {
	const { login } = useAuth();
	return <LandingPage onSignIn={(idpHint) => login(idpHint, "/")} />;
}
