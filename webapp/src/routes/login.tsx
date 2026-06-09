import { createFileRoute, redirect } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import { LoginCard } from "@/components/auth/LoginCard";
import { useAuth } from "@/integrations/auth/AuthContext";
import { ACCOUNT_DELETED_NOTICE_KEY } from "@/integrations/auth/accountDeletedNotice";
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
			// `href` (not `to`) is the typed escape hatch for a runtime-validated internal path:
			// it keeps type-checking on the target and, because safeReturnTo only ever returns a
			// relative path, the router treats it as an SPA navigation (no full reload).
			throw redirect({ href: safeReturnTo(search.returnTo) });
		}
	},
	component: LoginPage,
});

function LoginPage() {
	const { error, returnTo } = Route.useSearch();
	const { login } = useAuth();

	// One-shot confirmation after self-deletion: the delete flow signs out + reloads here, so a toast
	// fired before the reload would be lost. We stash a flag through the reload instead and announce
	// the real outcome (scheduled deletion, signed out everywhere) once on arrival.
	useEffect(() => {
		try {
			if (sessionStorage.getItem(ACCOUNT_DELETED_NOTICE_KEY) === "1") {
				sessionStorage.removeItem(ACCOUNT_DELETED_NOTICE_KEY);
				toast.success(
					"Your account is scheduled for deletion and you've been signed out everywhere. Permanent removal completes after about 48 hours.",
				);
			}
		} catch {
			// sessionStorage unavailable (private mode) — the notice is best-effort.
		}
	}, []);

	// Pass the validated ?returnTo destination through to the kickoff so the server echoes it
	// back into the SPA callback — without this the user would be returned to /login itself.
	return (
		<LoginCard
			title="Welcome to Hephaestus"
			description="Your AI mentor for growing as a software engineer."
			error={error}
			onSignIn={(registrationId) => login(registrationId, returnTo)}
		/>
	);
}
