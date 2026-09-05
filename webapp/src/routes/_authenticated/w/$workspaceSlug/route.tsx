import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";

// Every route under `w/$workspaceSlug/` inherits this gate. It answers on navigation only: a
// workspace revoked while a reader sits on one of its pages surfaces as the server's own 403s, since
// a redirect from a mounted page would carry that page's path into another workspace.
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug")({
	beforeLoad: async ({ context, params }) => {
		const workspaces = await context.queryClient
			.query(listWorkspacesOptions())
			.catch(() => undefined);
		// A list that cannot be fetched is not revoked access.
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
