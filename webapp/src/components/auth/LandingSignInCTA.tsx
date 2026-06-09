import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { ArrowRight } from "lucide-react";
import type { ComponentPropsWithoutRef } from "react";
import { listIdentityProvidersOptions } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type ButtonSize = ComponentPropsWithoutRef<typeof Button>["size"];

interface LandingSignInCTAProps {
	/** Whether the visitor is signed in (renders "Go to Dashboard" instead of "Sign in"). */
	isSignedIn: boolean;
	/** Start sign-in with a specific provider (used for the single-provider fast path). */
	onSignIn: (idpHint: string) => void;
	/** Navigate a signed-in visitor to their workspace. */
	onGoToDashboard?: () => void;
	size?: ButtonSize;
	className?: string;
}

/**
 * The landing page's single auth call-to-action — one clear action instead of a wall of provider
 * buttons repeated across every section. Signed-in visitors get "Go to Dashboard"; signed-out
 * visitors get "Sign in", which goes straight to the provider when only one is configured (the
 * common GitHub-only case) and otherwise opens the focused {@code /login} picker. {@code /login}
 * stays the single place a provider is chosen, so a visitor never picks a provider twice.
 */
export function LandingSignInCTA({
	isSignedIn,
	onSignIn,
	onGoToDashboard,
	size = "lg",
	className,
}: LandingSignInCTAProps) {
	const navigate = useNavigate();
	const { data: providers } = useQuery(listIdentityProvidersOptions());

	if (isSignedIn) {
		return (
			<Button size={size} className={cn("gap-2", className)} onClick={onGoToDashboard}>
				Go to Dashboard <ArrowRight className="h-4 w-4" />
			</Button>
		);
	}

	const handleSignIn = () => {
		// One provider → straight to OAuth (no pointless picker). Several (or not yet known) → the
		// focused /login screen, which presents each provider with proper branding and error handling.
		const soleProvider = providers?.length === 1 ? providers[0]?.registrationId : undefined;
		if (soleProvider) {
			onSignIn(soleProvider);
		} else {
			navigate({ to: "/login" });
		}
	};

	return (
		<Button size={size} className={cn("gap-2", className)} onClick={handleSignIn}>
			Sign in <ArrowRight className="h-4 w-4" />
		</Button>
	);
}
