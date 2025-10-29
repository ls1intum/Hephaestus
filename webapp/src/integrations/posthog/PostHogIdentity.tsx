import { usePostHog } from "posthog-js/react";
import { useEffect, useRef } from "react";
import { useAuth } from "../auth";

/**
 * PostHogIdentity Component
 *
 * This component handles user identification with PostHog after authentication.
 * It must be rendered inside both PostHogProvider and AuthProvider.
 */
export function PostHogIdentity() {
	const posthog = usePostHog();
	const { isAuthenticated, isLoading, userProfile, getUserId } = useAuth();
	const hasIdentified = useRef(false);

	useEffect(() => {
		// Wait for auth to finish loading
		if (isLoading) {
			return;
		}

		// If user is authenticated and we haven't identified them yet
		if (isAuthenticated && userProfile && !hasIdentified.current) {
			const userId = getUserId();

			if (!userId) {
				console.warn(
					"PostHogIdentity: User authenticated but no user ID available",
				);
				return;
			}

			const email = userProfile.email;
			const name =
				`${userProfile.firstName || ""} ${userProfile.lastName || ""}`.trim();
			const username = userProfile.username;

			posthog.identify(userId, {
				email,
				name,
				username,
			});

			hasIdentified.current = true;
		}

		// If user logs out, reset PostHog
		if (!isAuthenticated && hasIdentified.current) {
			posthog.reset();
			hasIdentified.current = false;
		}
	}, [isAuthenticated, isLoading, userProfile, getUserId, posthog]);

	// This component doesn't render anything
	return null;
}
