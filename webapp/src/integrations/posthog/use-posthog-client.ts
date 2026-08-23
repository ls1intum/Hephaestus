import type { PostHog } from "posthog-js";
import { usePostHog } from "posthog-js/react";

/**
 * Always resolves to a client: `posthog-js/react` seeds its context default with the `posthog-js`
 * module singleton, so outside a `PostHogProvider` this hands back an un-`init`ed instance rather
 * than nothing. Holding a reference is no evidence that capturing is live — `isPosthogEnabled` is
 * the gate.
 */
export function usePostHogClient(): PostHog {
	return usePostHog();
}
