import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	Outlet,
	RouterProvider,
} from "@tanstack/react-router";
import { act, render } from "@testing-library/react";
import type { ReactNode } from "react";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { routeTree } from "@/routeTree.gen";

/**
 * The first `findBy` after {@link renderRouteAt} pays the lazy transform of the whole admin layout,
 * which runs well past `findBy`'s own 1s default. Raising `testTimeout` alone does not cover it.
 */
export const TRANSFORM_WAIT = { timeout: 10_000 } as const;

/** Retries off, so a failing request fails once and reports instead of timing the test out. */
export function testQueryClient(): QueryClient {
	return new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
}

/**
 * Renders the app's real router at `path`, rather than a route's `component` directly: a route's
 * `beforeLoad` gate, its `head` and anything it reads off the URL only exist when the route is
 * matched, so rendering the component alone cannot tell a working route from an unreachable one.
 *
 * Pass a `queryClient` to seed the cache first. The same client backs the route guards and the
 * provider.
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

export async function renderWithRouter(node: ReactNode, initialPath: string) {
	const rootRoute = createRootRoute({
		component: () => (
			<>
				{node}
				<Outlet />
			</>
		),
	});
	const stubTree = rootRoute.addChildren([
		createRoute({ getParentRoute: () => rootRoute, path: "/", component: () => null }),
		createRoute({ getParentRoute: () => rootRoute, path: "$", component: () => null }),
	]);
	const router = createRouter({
		routeTree: stubTree,
		history: createMemoryHistory({ initialEntries: [initialPath] }),
	});
	let result!: ReturnType<typeof render>;
	await act(async () => {
		result = render(<RouterProvider router={router} />);
		await router.load();
	});
	return { ...result, router };
}
