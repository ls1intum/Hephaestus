import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	Outlet,
	RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useWorkspaceSwitcher } from "./use-workspace-switcher";

vi.mock("sonner", () => ({ toast: { info: vi.fn() } }));

function SwitchWorkspace() {
	const switchWorkspace = useWorkspaceSwitcher();
	return (
		<>
			<button
				onClick={() =>
					void switchWorkspace({ displayName: "Beta workspace", workspaceSlug: "beta" })
				}
			>
				Switch workspace
			</button>
			<button
				onClick={() =>
					void switchWorkspace({ displayName: "Alpha workspace", workspaceSlug: "alpha" })
				}
			>
				Keep workspace
			</button>
		</>
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
	const user = userEvent.setup();
	await user.click(await screen.findByRole("button", { name: "Switch workspace" }));
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
		[
			"practice",
			"/w/alpha/admin/practices/testing?status=foreign",
			"admin/practices/$practiceSlug",
		],
	])("falls back to workspace home from a %s", async (_name, initialEntry, path) => {
		const router = renderRoute(initialEntry, path);

		await clickWorkspaceSwitch();

		await waitFor(() => expect(router.state.location.href).toBe("/w/beta"));
		expect(toast.info).toHaveBeenCalledExactlyOnceWith("Switched to Beta workspace", {
			description:
				"This page is specific to the previous workspace, so we opened the new workspace's home page.",
		});
	});

	it("does nothing when the selected workspace is already active", async () => {
		const router = renderRoute("/w/alpha/mentor/thread-1?message=current", "mentor/$threadId");
		const user = userEvent.setup();

		await user.click(await screen.findByRole("button", { name: "Keep workspace" }));

		expect(router.state.location.href).toBe("/w/alpha/mentor/thread-1?message=current");
		expect(toast.info).not.toHaveBeenCalled();
	});
});
