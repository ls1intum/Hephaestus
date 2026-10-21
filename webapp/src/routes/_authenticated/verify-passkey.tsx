import { createFileRoute, useCanGoBack, useLocation, useRouter } from "@tanstack/react-router";
import { useRef } from "react";

import { ConfirmAccessDialog } from "@/components/auth/ConfirmAccessDialog";
import {
	PasskeyChallengePage,
	type PasskeyChallengeState,
} from "@/components/auth/PasskeyChallengePage";
import { usePasskeySettings } from "@/hooks/use-passkey-settings";
import { pageHead } from "@/lib/page-title";
import { passkeyLeaveTo, passkeyReturnTo } from "@/runtime/auth/passkey-challenge";

interface VerifyPasskeySearch {
	returnTo?: string;
}

export const Route = createFileRoute("/_authenticated/verify-passkey")({
	head: pageHead("Confirm with a passkey"),
	staticData: { surface: "auth" },
	validateSearch: (search): VerifyPasskeySearch => ({
		returnTo: typeof search.returnTo === "string" ? search.returnTo : undefined,
	}),
	component: VerifyPasskeyRoute,
});

/** The challenge entry a confirmation started on: the router's history key and address. */
interface StartedOn {
	key: string | undefined;
	href: string;
	returnTo: string | undefined;
}

function VerifyPasskeyRoute() {
	const { returnTo } = Route.useSearch();
	const router = useRouter();
	const canGoBack = useCanGoBack();
	const location = useLocation();
	const startedOn = useRef<StartedOn>(undefined);
	const { section, confirmation, completed } = usePasskeySettings({
		// A recent sign-in before a first passkey comes back to this exact challenge.
		signInReturnTo: location.href,
		onVerified: () => {
			// The server's acceptance stands either way. Only the challenge the developer is still on
			// continues, to the destination it was opened with: an answer that arrives after they
			// left, or opened another challenge, leaves their current page alone.
			const started = startedOn.current;
			const here = router.state.location;
			if (
				started === undefined ||
				started.href !== here.href ||
				started.key !== here.state.__TSR_key
			) {
				return;
			}
			void router.navigate({ href: passkeyReturnTo(started.returnTo), replace: true });
		},
	});
	const confirm = () => {
		const here = router.state.location;
		startedOn.current = { key: here.state.__TSR_key, href: here.href, returnTo };
		section.onVerify();
	};
	// The challenge replaced the refused page, so Back leads to the page before it. Without one, as
	// after a sign-in or an opened link, the workspace it was asked from is where the developer was.
	const leave = () => {
		if (canGoBack) {
			router.history.back();
		} else {
			void router.navigate({ href: passkeyLeaveTo(returnTo), replace: true });
		}
	};

	let state: PasskeyChallengeState;
	if (section.loading) {
		state = { status: "loading" };
	} else if (section.status === undefined) {
		state = {
			status: "error",
			message: section.error ?? "Check your connection, then try again.",
			onRetry: section.onRetry,
		};
	} else {
		const step = section.status.credentials.length > 0 ? "confirm" : "add";
		let pending: "add" | "confirm" | undefined;
		if (section.pending) {
			pending = section.pendingAction === "register" ? "add" : "confirm";
		}
		state = {
			status: "ready",
			step,
			justAdded: completed === "register",
			recovering: section.status.recoveryRequired,
			supported: section.supported,
			pending,
			error: section.error,
		};
	}

	return (
		<>
			<PasskeyChallengePage
				state={state}
				// No name first: the passkey gets a dated default that settings shows.
				onAdd={() => section.onRegister("")}
				onConfirm={confirm}
				onLeave={leave}
			/>
			<ConfirmAccessDialog {...confirmation} />
		</>
	);
}
