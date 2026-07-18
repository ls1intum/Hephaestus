import { onlineManager } from "@tanstack/react-query";
import { RssIcon, WifiOffIcon } from "lucide-react";
import { useSyncExternalStore } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useLivePushUnavailable } from "@/hooks/use-sync-liveness";

/**
 * Whether the browser currently has a network connection, per TanStack Query's own view of it.
 *
 * Reading `onlineManager` rather than `navigator.onLine` directly is what makes this honest: the
 * manager is the same signal that decides whether queries run, so the banner and the data can never
 * disagree. It is a proper external store — `subscribe` returns an unsubscribe, `isOnline()` returns a
 * stable primitive — so `useSyncExternalStore` is the idiomatic read, tear-free under concurrent
 * rendering and correctly initialised without an effect round-trip.
 *
 * The server snapshot is `true`: a render with no browser to ask has no evidence of being offline, and
 * flashing an offline banner during hydration would be its own lie.
 */
export function useIsOnline(): boolean {
	return useSyncExternalStore(
		(onStoreChange) => onlineManager.subscribe(onStoreChange),
		() => onlineManager.isOnline(),
		() => true,
	);
}

/**
 * The section-wide freshness note: says, in one place, when the numbers below have stopped being live.
 *
 * This surface exists to tell the truth about sync freshness, which makes silence about its own
 * staleness the worst failure it has. Two things break liveness, and they are not equally bad:
 *
 * - **Offline.** Query v5 defaults to `networkMode: "online"`, so a dropped connection *pauses*
 *   queries rather than failing them. Nothing throws, so no `QueryErrorAlert` ever fires and a live
 *   progress bar simply freezes mid-run, indefinitely, looking exactly like a healthy one. Polling
 *   cannot help either, so everything below is a snapshot of the past.
 * - **Live push lost.** The SSE stream is down but HTTP still works, so polling takes over. The data
 *   is still arriving, just later than it should.
 *
 * Offline strictly outranks live-push-lost — a dead network makes the polling fallback moot — so it
 * replaces the note rather than stacking a second bar on top of it.
 *
 * Rendered inside `SyncLivenessProvider`, which is where the section's one SSE stream publishes its
 * health, so the component needs no props.
 */
export function SyncFreshnessBanner() {
	const isOnline = useIsOnline();
	const livePushUnavailable = useLivePushUnavailable();

	if (!isOnline) {
		return (
			<Alert
				variant="warning"
				role="status"
				aria-live="polite"
				className="rounded-none border-x-0 border-t-0 px-6 py-2"
			>
				<WifiOffIcon />
				<AlertTitle>You're offline — everything below is a snapshot</AlertTitle>
				<AlertDescription>
					Sync status stopped updating when the connection dropped. It will catch up on its own once
					you're back.
				</AlertDescription>
			</Alert>
		);
	}

	if (livePushUnavailable) {
		return (
			<Alert
				role="status"
				aria-live="polite"
				className="rounded-none border-x-0 border-t-0 px-6 py-2"
			>
				<RssIcon />
				<AlertTitle>Live updates are unavailable</AlertTitle>
				<AlertDescription>This section is refreshing periodically instead.</AlertDescription>
			</Alert>
		);
	}

	return null;
}
