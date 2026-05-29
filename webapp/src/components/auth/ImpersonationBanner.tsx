import { useMutation } from "@tanstack/react-query";
import { useEffect } from "react";
import { exitImpersonationMutation } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";

/**
 * Persistent amber banner shown across the top of the app while the current session is
 * impersonating another account. Reads impersonation state from the current user (via
 * {@link useAuth}); renders nothing when not impersonating, so it is safe to always mount.
 *
 * Amber (not red) is intentional: red is reserved for errors. "Stop impersonating" exits via
 * the server mutation and does a full reload so the session cookie and current-user re-resolve
 * (mirroring how the admin "Impersonate" action navigates).
 */
export function ImpersonationBanner() {
	const { isImpersonating, impersonatedDisplayName } = useAuth();

	const exit = useMutation({
		...exitImpersonationMutation(),
		onSuccess: () => {
			// Full reload so the restored operator session cookie + current-user re-resolve cleanly.
			window.location.assign("/");
		},
	});

	// Expose a global CSS hook while impersonating (cleaned up when the banner unmounts/stops).
	useEffect(() => {
		if (!isImpersonating) {
			return;
		}
		document.body.setAttribute("data-impersonating", "true");
		return () => {
			document.body.removeAttribute("data-impersonating");
		};
	}, [isImpersonating]);

	if (!isImpersonating) {
		return null;
	}

	const displayName = impersonatedDisplayName ?? "another account";

	return (
		<div
			role="status"
			aria-live="polite"
			className="sticky top-0 z-50 flex w-full items-center justify-center gap-x-3 gap-y-1 flex-wrap border-b border-amber-300 bg-amber-100 px-4 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100"
		>
			<span>
				Impersonating <strong className="font-semibold">{displayName}</strong>
				<span className="mx-2 opacity-60">·</span>
				read-only
			</span>
			<Button
				variant="outline"
				size="sm"
				disabled={exit.isPending}
				onClick={() => exit.mutate({})}
				aria-label="Stop impersonating and restore your account"
				className="h-7 border-amber-400 bg-transparent text-amber-900 hover:bg-amber-200 dark:border-amber-700 dark:text-amber-100 dark:hover:bg-amber-900"
			>
				{exit.isPending ? <Spinner className="mr-2 size-3.5" /> : null}
				Stop impersonating
			</Button>
		</div>
	);
}
