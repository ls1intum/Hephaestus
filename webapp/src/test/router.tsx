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

/**
 * Render a component that uses router primitives (`Link`, `useNavigate`) under a real memory
 * router. Resolves once the router has mounted, so assertions can then be synchronous.
 *
 * `initialPath` seeds the history. Pass one whenever a test asserts navigation: starting at the
 * destination would let the assertion pass without the component ever navigating.
 */
export async function renderWithRouter(node: ReactNode, initialPath = "/") {
	// The node renders in the root layout, so navigation changes the path without remounting it —
	// otherwise a remount would refetch its queries and muddy what a test is measuring. The empty
	// children exist only so `/` and any `initialPath` are both real, matchable destinations.
	const rootRoute = createRootRoute({
		component: () => (
			<>
				{node}
				<Outlet />
			</>
		),
	});
	const routeTree = rootRoute.addChildren([
		createRoute({ getParentRoute: () => rootRoute, path: "/", component: () => null }),
		createRoute({ getParentRoute: () => rootRoute, path: "$", component: () => null }),
	]);
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: [initialPath] }),
	});
	let result!: ReturnType<typeof render>;
	// The router's mount resolves after render, so both steps belong in the same act() — otherwise
	// React reports the state update it produces as an unwrapped one.
	await act(async () => {
		result = render(<RouterProvider router={router} />);
		await router.load();
	});
	return { ...result, router };
}
