import { createFileRoute, redirect } from "@tanstack/react-router";
import { LoginCard } from "@/components/auth/LoginCard";
import { useAuth } from "@/integrations/auth/AuthContext";
import { resolveCurrentUser, safeReturnTo } from "@/integrations/auth/guard";

interface LoginSearch {
	returnTo?: string;
	error?: string;
}

export const Route = createFileRoute("/login")({
	validateSearch: (search): LoginSearch => ({
		returnTo: typeof search.returnTo === "string" ? search.returnTo : undefined,
		error: typeof search.error === "string" ? search.error : undefined,
	}),
	// Bounce already-authenticated users away from the login page to their intended
	// destination (the validated ?returnTo, default "/"). Resolving through the query
	// client means the first paint is correct (no login-card flash for signed-in users).
	// This also closes the loop when the server lands an authenticated user back on /login.
	beforeLoad: async ({ context, search }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (user) {
			throw redirect({ to: safeReturnTo(search.returnTo) as never });
		}
	},
	component: LoginPage,
});

function LoginPage() {
	const { error } = Route.useSearch();
	const { login } = useAuth();

	// The server kickoff (/auth/login) reads returnTo from the current URL, so the ?returnTo=
	// param is already carried along when login() triggers the top-level redirect.
	return (
		<LoginCard
			title="Sign in to Hephaestus"
			description="Continue with your Git provider to access your workspaces."
			error={error}
			onSignIn={(registrationId) => login(registrationId)}
		/>
	);
}
