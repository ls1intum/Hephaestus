import { createFileRoute, redirect } from "@tanstack/react-router";

// Back-compat redirect: AI models live at /admin/models.
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/ai/agents")({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/models",
			params: { workspaceSlug: params.workspaceSlug },
			search: (prev) => prev,
		});
	},
});
