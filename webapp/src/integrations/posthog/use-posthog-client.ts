import type { PostHog } from "posthog-js";
import { usePostHog } from "posthog-js/react";

/**
 * The PostHog client, or `undefined` when none is available.
 *
 * `usePostHog` is declared as always returning a client, but it reads a context whose default value
 * is the module-level instance a `PostHogProvider` installs — so outside a provider it hands back
 * `undefined`. This app mounts the provider only once analytics consent is granted and PostHog is
 * configured, which makes "no client" the ordinary case rather than an edge case, and every caller
 * has to handle it.
 */
export function usePostHogClient(): PostHog | undefined {
	const client: PostHog | undefined = usePostHog();
	return client;
}
