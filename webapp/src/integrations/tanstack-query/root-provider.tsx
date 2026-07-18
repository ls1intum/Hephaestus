import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			/**
			 * The default of 0 refetches on every mount, refocus and reconnect, which stacks with the
			 * SSE hints and poll intervals already keeping this app fresh and hits the same endpoint in
			 * bursts. 30s deduplicates those passive triggers without weakening a deliberate one:
			 * invalidation marks a query stale regardless of `staleTime`, so a hint or mutation still
			 * refetches immediately.
			 *
			 * Not `Infinity`: SSE hint delivery is at-most-once, so `refetchOnWindowFocus` (default
			 * `true`) must stay armed as the catch-up-after-absence healer.
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
