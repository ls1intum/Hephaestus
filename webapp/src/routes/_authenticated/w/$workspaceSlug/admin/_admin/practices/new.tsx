import { createFileRoute, redirect } from "@tanstack/react-router";

// Moved to /admin/ai/practice-detection/catalog/new.
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices/new")(
	{
		beforeLoad: ({ params }) => {
			throw redirect({
				to: "/w/$workspaceSlug/admin/ai/practice-detection/catalog/new",
				params: { workspaceSlug: params.workspaceSlug },
				search: (prev) => prev,
			});
		},
	},
);
