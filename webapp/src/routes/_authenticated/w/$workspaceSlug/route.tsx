import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";

/** Workspace gate: a directory layout, so every route under `w/$workspaceSlug/` inherits it. */
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug")({
	beforeLoad: async ({ context, params }) => {
		const workspaces = await context.queryClient
			.query(listWorkspacesOptions())
			.catch(() => undefined);
		// A list that cannot be fetched is not revoked access: keep the route rather than evicting
		// the reader on a network error.
		if (!workspaces) return;
		if (workspaces.some((workspace) => workspace.workspaceSlug === params.workspaceSlug)) return;

		const fallbackSlug = workspaces[0]?.workspaceSlug;
		if (!fallbackSlug) throw redirect({ to: "/", replace: true });
		throw redirect({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: fallbackSlug },
			replace: true,
		});
	},
	component: () => <Outlet />,
});
