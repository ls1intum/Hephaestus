import { useQuery } from "@tanstack/react-query";
import { usePostHog } from "posthog-js/react";
import { useEffect, useRef } from "react";
import { getUserSettingsOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "../auth";

/**
 * PostHogIdentity Component
 *
 * This component handles user identification with PostHog after authentication.
 * It must be rendered inside both PostHogProvider and AuthProvider.
 * It also manages PostHog opt-out based on user settings.
 */
export function PostHogIdentity() {
	const posthog = usePostHog();
	const { isAuthenticated, isLoading, userProfile, getUserId } = useAuth();
	const hasIdentified = useRef(false);

	// Fetch user settings to check research opt-out preference
	const { data: settings, isLoading: settingsLoading } = useQuery({
		...getUserSettingsOptions({}),
		enabled: isAuthenticated && !isLoading,
	});

	useEffect(() => {
		// Wait for auth and settings to finish loading
		if (isLoading || settingsLoading) {
			return;
		}

		// If user has opted out of research, opt out of PostHog tracking
		if (isAuthenticated && settings?.researchOptOut) {
			if (hasIdentified.current) {
				// Reset PostHog if previously identified
				posthog.reset();
				hasIdentified.current = false;
			}
			// Opt out of capturing
			posthog.opt_out_capturing();
			return;
		}

		// If user is authenticated and hasn't opted out
		if (isAuthenticated && userProfile && !hasIdentified.current) {
			const userId = getUserId();

			if (!userId) {
				console.warn(
					"PostHogIdentity: User authenticated but no user ID available",
				);
				return;
			}

			// Opt in to capturing in case they were previously opted out
			posthog.opt_in_capturing();

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
	}, [
		isAuthenticated,
		isLoading,
		settingsLoading,
		userProfile,
		getUserId,
		posthog,
		settings?.researchOptOut,
	]);

	// This component doesn't render anything
	return null;
}
