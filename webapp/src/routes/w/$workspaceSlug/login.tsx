import { useQuery } from "@tanstack/react-query";
import { createFileRoute, redirect } from "@tanstack/react-router";
import { getWorkspaceOptions } from "@/api/@tanstack/react-query.gen";
import { LoginCard } from "@/components/auth/LoginCard";
import { useAuth } from "@/integrations/auth/AuthContext";
import { resolveCurrentUser } from "@/integrations/auth/guard";

interface WorkspaceLoginSearch {
	error?: string;
}

export const Route = createFileRoute("/w/$workspaceSlug/login")({
	validateSearch: (search): WorkspaceLoginSearch => ({
		error: typeof search.error === "string" ? search.error : undefined,
	}),
	// Already-authenticated users go straight into the workspace. Resolving through the
	// query client keeps the first paint correct (no login-card flash). When the server
	// lands an authenticated user back here after login, this closes the loop too.
	beforeLoad: async ({ context, params }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (user) {
			throw redirect({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug: params.workspaceSlug },
			});
		}
	},
	component: WorkspaceLoginPage,
});

function WorkspaceLoginPage() {
	const { workspaceSlug } = Route.useParams();
	const { error } = Route.useSearch();
	const { login } = useAuth();

	// No workspace-scoped identity-provider endpoint exists; LoginCard falls back to the
	// global listIdentityProviders. We still surface the workspace name when it's public.
	const { data: workspace } = useQuery({
		...getWorkspaceOptions({ path: { workspaceSlug } }),
		staleTime: 5 * 60 * 1000,
		retry: false,
	});

	const heading = workspace?.displayName
		? `Sign in to ${workspace.displayName}`
		: "Sign in to your workspace";

	return (
		<LoginCard
			title={heading}
			description="Sign in to continue to this workspace."
			error={error}
			onSignIn={(registrationId) => login(registrationId, `/w/${workspaceSlug}`)}
		/>
	);
}
