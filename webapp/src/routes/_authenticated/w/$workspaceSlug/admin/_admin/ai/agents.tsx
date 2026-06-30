import { createFileRoute, redirect } from "@tanstack/react-router";

// Renamed: "AI models" is now the workspace-infrastructure "Models" section at /admin/models.
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/ai/agents")({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/models",
			params: { workspaceSlug: params.workspaceSlug },
			search: (prev) => prev,
		});
	},
});
