import { createFileRoute, redirect } from "@tanstack/react-router";

// Back-compat redirect: workspace AI setup lives at /admin/models.
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/ai/agents")({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/models",
			params: { workspaceSlug: params.workspaceSlug },
			search: (prev) => prev,
		});
	},
});
