import { createFileRoute, redirect } from "@tanstack/react-router";

/** Retired. "Review settings" is now the *When and where* section of the one Review page. */
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/settings")({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/review",
			params,
			search: { section: "when-and-where" },
		});
	},
});
