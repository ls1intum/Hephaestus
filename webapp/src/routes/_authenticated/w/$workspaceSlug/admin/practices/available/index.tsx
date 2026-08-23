import { createFileRoute, redirect } from "@tanstack/react-router";

/** Kept so a bookmarked link lands on Practice setup with the catalog shown. */
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
