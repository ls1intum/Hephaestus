import type { PostHog } from "posthog-js";
import { usePostHog } from "posthog-js/react";

/**
 * `usePostHog` is declared as always returning a client, but outside a `PostHogProvider` the context
 * default is `undefined`. The provider mounts only once analytics consent is granted and PostHog is
 * configured, so "no client" is the ordinary case and every caller has to handle it.
 */
export function usePostHogClient(): PostHog | undefined {
	const client: PostHog | undefined = usePostHog();
	return client;
}
