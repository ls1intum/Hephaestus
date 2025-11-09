import { useQuery } from "@tanstack/react-query";
import { usePostHog } from "posthog-js/react";
import { useEffect, useMemo, useRef } from "react";

import { getUserSettingsOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "../auth";
import { isPosthogEnabled } from "./config";

/**
 * Handles user identification and consent-aware tracking with PostHog.
 */
export function PostHogIdentity() {
	const posthog = usePostHog();
	const { isAuthenticated, isLoading, userProfile, getUserId } = useAuth();
	const hasIdentified = useRef(false);

	const {
		data: userSettings,
		isLoading: isSettingsLoading,
		isError: isSettingsError,
	} = useQuery({
		...getUserSettingsOptions({}),
		enabled: isAuthenticated && isPosthogEnabled,
		retry: 1,
	});

	const participatesInResearch = userSettings?.participateInResearch;
	const shouldDenyTracking = useMemo(() => {
		if (!isAuthenticated) {
			return false;
		}
		if (isSettingsError) {
			return true;
		}
		return participatesInResearch !== true;
	}, [isAuthenticated, participatesInResearch, isSettingsError]);

	useEffect(() => {
		if (!posthog || !isPosthogEnabled) {
			return;
		}

		if (isLoading || isSettingsLoading) {
			// Default to denied until consent is explicit; ensure client stays opted-out while loading
			posthog.opt_out_capturing();
			return;
		}

		if (!isAuthenticated) {
			if (hasIdentified.current) {
				posthog.reset();
				hasIdentified.current = false;
			}
			return;
		}

		if (shouldDenyTracking) {
			posthog.opt_out_capturing();
			posthog.reset();
			posthog.stopSessionRecording?.();
			posthog.getActiveMatchingSurveys?.(() => {}, true);
			hasIdentified.current = false;
			return;
		}

		const userId = getUserId();
		if (!userId) {
			return;
		}

		posthog.opt_in_capturing();

		if (userProfile && !hasIdentified.current) {
			const email = userProfile.email;
			const name =
				`${userProfile.firstName || ""} ${userProfile.lastName || ""}`.trim();
			const username = userProfile.username;

			posthog.identify(userId, {
				email,
				name,
				username,
				participate_in_research: participatesInResearch === true,
			});

			hasIdentified.current = true;
		}
	}, [
		posthog,
		isLoading,
		isSettingsLoading,
		isAuthenticated,
		shouldDenyTracking,
		getUserId,
		userProfile,
		participatesInResearch,
	]);

	return null;
}
