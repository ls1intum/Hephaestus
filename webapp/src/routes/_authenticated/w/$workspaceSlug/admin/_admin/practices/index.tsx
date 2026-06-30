import { createFileRoute, redirect } from "@tanstack/react-router";

// Moved to /admin/ai/practice-detection (the Rubric tree).
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices/")({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/ai/practice-detection",
			params: { workspaceSlug: params.workspaceSlug },
			search: (prev) => prev,
		});
	},
});
