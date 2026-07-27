import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { routeTree } from "@/routeTree.gen";

/**
 * `findBy`'s own timeout for the first assertion after {@link renderRouteAt}, which is what actually
 * decides these — separate from the file's `testTimeout`. The first mount in a file pays the lazy
 * transform of the whole admin layout and its route modules: seconds under a loaded box, well past
 * the 1s default.
 */
export const TRANSFORM_WAIT = { timeout: 10_000 } as const;

/** The client `main.tsx` wires, with retries off so a failing request fails once and reports. */
export function testQueryClient(): QueryClient {
	return new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
}

/**
 * Renders the app's real router at `path`, rather than a route's `component` directly: a route's
 * `beforeLoad` gate, its `head` and anything it reads off the URL only exist when the route is
 * matched, so a test that renders the component alone cannot tell a working route from an
 * unreachable one.
 *
 * Pass a `queryClient` to seed the cache first; otherwise one is made here. The same client backs
 * the route guards and the provider, as in `main.tsx`.
 */
export function renderRouteAt(path: string, queryClient: QueryClient = testQueryClient()) {
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: [path] }),
		context: { queryClient, auth: undefined },
	});
	render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				{/* biome-ignore lint/suspicious/noExplicitAny: the app's router context is wider than any one route test needs. */}
				<RouterProvider router={router as any} />
			</AuthProvider>
		</QueryClientProvider>,
	);
	return queryClient;
}
