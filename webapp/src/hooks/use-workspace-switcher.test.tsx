import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	Outlet,
	RouterProvider,
} from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useWorkspaceSwitcher } from "./use-workspace-switcher";

vi.mock("sonner", () => ({ toast: { info: vi.fn() } }));

function SwitchWorkspace() {
	const switchWorkspace = useWorkspaceSwitcher();
	return (
		<button
			onClick={() => switchWorkspace({ displayName: "Beta workspace", workspaceSlug: "beta" })}
		>
			Switch workspace
		</button>
	);
}

function renderRoute(initialEntry: string, path: string) {
	const rootRoute = createRootRoute({ component: Outlet });
	const workspaceRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "w/$workspaceSlug",
		component: Outlet,
	});
	const currentRoute = createRoute({
		getParentRoute: () => workspaceRoute,
		path,
		component: SwitchWorkspace,
	});
	const indexRoute = createRoute({
		getParentRoute: () => workspaceRoute,
		path: "/",
		component: () => null,
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([workspaceRoute.addChildren([indexRoute, currentRoute])]),
		history: createMemoryHistory({ initialEntries: [initialEntry] }),
	});

	render(<RouterProvider router={router} />);
	return router;
}

async function clickWorkspaceSwitch() {
	fireEvent.click(await screen.findByRole("button", { name: "Switch workspace" }));
}

describe("useWorkspaceSwitcher", () => {
	beforeEach(() => vi.clearAllMocks());

	it("keeps a portable route and clears its search", async () => {
		const router = renderRoute("/w/alpha/teams?tab=members", "teams");

		await clickWorkspaceSwitch();

		await waitFor(() => expect(router.state.location.href).toBe("/w/beta/teams"));
		expect(toast.info).not.toHaveBeenCalled();
	});

	it.each([
		["mentor thread", "/w/alpha/mentor/thread-1?message=foreign", "mentor/$threadId"],
		["user profile", "/w/alpha/user/octocat?group=foreign", "user/$username"],
	])("falls back to workspace home from a %s", async (_name, initialEntry, path) => {
		const router = renderRoute(initialEntry, path);

		await clickWorkspaceSwitch();

		await waitFor(() => expect(router.state.location.href).toBe("/w/beta"));
		expect(toast.info).toHaveBeenCalledExactlyOnceWith("Switched to Beta workspace", {
			description:
				"This page is specific to the previous workspace, so we opened the new workspace's home page.",
		});
	});
});
