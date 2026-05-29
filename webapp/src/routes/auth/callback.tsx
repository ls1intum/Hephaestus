import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { safeReturnTo } from "@/integrations/auth/guard";

interface CallbackSearch {
	returnTo?: string;
}

/**
 * SPA landing after the server sets the session cookie. Reads the validated `?returnTo`
 * and navigates there once the auth query has settled, so the destination renders with a
 * known auth state instead of flashing the unauthenticated view.
 */
export const Route = createFileRoute("/auth/callback")({
	validateSearch: (search): CallbackSearch => ({
		returnTo: typeof search.returnTo === "string" ? search.returnTo : undefined,
	}),
	component: AuthCallbackPage,
});

function AuthCallbackPage() {
	const { returnTo } = Route.useSearch();
	const { isLoading } = useAuth();
	const navigate = useNavigate();

	useEffect(() => {
		// Wait for the cookie-session probe (GET /user) to settle so the target route
		// paints with the correct auth state.
		if (isLoading) return;
		navigate({ to: safeReturnTo(returnTo) as never, replace: true });
	}, [isLoading, returnTo, navigate]);

	return (
		<div className="flex min-h-[100dvh] items-center justify-center">
			<Spinner className="size-8" aria-label="Signing you in" />
		</div>
	);
}
