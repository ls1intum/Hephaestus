import { createFileRoute, redirect } from "@tanstack/react-router";

/** Retired. The library is a section of Practice setup, not a page of its own. */
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/available/")(
	{
		beforeLoad: ({ params }) => {
			throw redirect({
				to: "/w/$workspaceSlug/admin/practices",
				params,
				search: { library: true },
			});
		},
	},
);
