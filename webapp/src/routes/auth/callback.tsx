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
	const { isLoading, isError } = useAuth();
	const navigate = useNavigate();

	useEffect(() => {
		// Wait for the cookie-session probe (GET /user) to settle so the target route
		// paints with the correct auth state.
		if (isLoading) return;
		// If the probe errored (401/403/network) the session cookie wasn't accepted — route
		// straight to /login rather than optimistically navigating into a protected target and
		// relying on the `_authenticated` guard to bounce us back.
		if (isError) {
			navigate({ to: "/login", replace: true });
			return;
		}
		// `href` (not `to`) carries the runtime-validated internal path while keeping the typed
		// navigate API: safeReturnTo only returns relative paths, so this stays an SPA navigation.
		navigate({ href: safeReturnTo(returnTo), replace: true });
	}, [isLoading, isError, returnTo, navigate]);

	return (
		<div className="flex min-h-[100dvh] items-center justify-center">
			<Spinner className="size-8" aria-label="Signing you in" />
		</div>
	);
}
