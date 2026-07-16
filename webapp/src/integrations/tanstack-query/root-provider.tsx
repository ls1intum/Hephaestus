import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			/**
			 * The default of 0 makes data stale the instant it arrives, so every mount, refocus and
			 * reconnect refetches — and those triggers stack with the SSE hints and poll intervals that
			 * already keep this app fresh, hitting the same endpoint several times in a burst. 30s
			 * deduplicates the passive triggers without weakening any deliberate one: invalidation
			 * marks a query stale regardless of `staleTime`, so a hint or a mutation still refetches
			 * immediately.
			 *
			 * Not `Infinity`: SSE hint delivery is at-most-once, so `refetchOnWindowFocus` (default
			 * `true`) has to stay armed as the catch-up-after-absence healer.
			 */
			staleTime: 30_000,
		},
	},
});

export function getContext() {
	return {
		queryClient,
	};
}

export function Provider({ children }: { children: React.ReactNode }) {
	return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
