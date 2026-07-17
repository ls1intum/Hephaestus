import { ShieldAlert } from "lucide-react";
import { SignInButtons } from "@/components/auth/SignInButtons";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/integrations/auth/AuthContext";
import { authClient } from "@/integrations/auth/authClient";

export interface ConfirmAccessDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** How recent the sign-in must be, from the server's challenge (`maxAgeSeconds`). */
	maxAgeSeconds?: number;
	/** Where to land after the round-trip. Defaults to the current page. */
	returnTo?: string;
}

function describeWindow(maxAgeSeconds: number | undefined): string {
	if (maxAgeSeconds == null) return "a recent sign-in";
	// floor, not round: rounding 45s up to "1 minute" would overstate the permitted window (unsafe).
	const minutes = Math.floor(maxAgeSeconds / 60);
	return minutes >= 1
		? `a sign-in from the last ${minutes} minute${minutes === 1 ? "" : "s"}`
		: `a sign-in from the last ${maxAgeSeconds} seconds`;
}

/**
 * Step-up re-auth prompt (GitHub's "confirm access" pattern), shown on a `403 step_up_required`.
 * Signing in again re-stamps `auth_time` on the session and returns to `returnTo`. It is a full-page
 * round-trip through the identity provider, so the caller's dialog state does NOT survive it — the
 * admin re-initiates the action on return.
 */
export function ConfirmAccessDialog({
	open,
	onOpenChange,
	maxAgeSeconds,
	returnTo,
}: ConfirmAccessDialogProps) {
	// Prefer the admin's own provider TYPE — signing in with a different one starts a fresh login for
	// that identity rather than confirming this account (SignInButtons falls back to all providers if
	// the type matches none, e.g. a link-only primary identity).
	const providerType = useAuth().userProfile?.identityProvider;
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent>
				<DialogHeader>
					<DialogTitle className="flex items-center gap-2">
						<ShieldAlert className="size-5 text-muted-foreground" aria-hidden />
						Confirm access
					</DialogTitle>
					<DialogDescription>
						This action needs {describeWindow(maxAgeSeconds)}. Sign in again to confirm it&apos;s
						you — you&apos;ll return to this page to retry.
					</DialogDescription>
				</DialogHeader>
				<SignInButtons
					onSignIn={(idpHint) =>
						authClient.login(
							idpHint,
							returnTo ?? `${window.location.pathname}${window.location.search}`,
						)
					}
					onlyProviderType={providerType}
				/>
			</DialogContent>
		</Dialog>
	);
}
